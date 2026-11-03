package com.nac.auth.service;

import cn.hutool.captcha.LineCaptcha;
import cn.hutool.captcha.generator.CodeGenerator;
import cn.hutool.captcha.generator.RandomGenerator;
import cn.hutool.core.util.IdUtil;
import com.nac.common.constant.RedisKeyConstants;
import com.nac.common.redis.RedisUtil;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 图形验证码：生成（存 Redis 300s）+ 校验后销毁（防重放）。
 *
 * <p>渲染不再使用 Hutool 默认的「彩色随机字符 + 随机干扰线」（其半透明浅色系会灰蒙蒙）。
 * 这里自绘「彩色高对比」版本：纯白底 + 彩色干扰线/噪点/弧线 + 高饱和深色字符
 * （逐字随机颜色 + 小角度旋转），兼顾人眼可读与机器识别难度。
 */
@Slf4j
@Service
public class CaptchaService {

    private static final int TTL = 300;

    /**
     * 字符集：仅大写字母 + 数字，剔除易混淆字符 0/O、1/I/L、2/Z、5/S，
     * 避免用户看错（校验本身不区分大小写，输入小写亦可通过）。
     */
    private static final String CAPTCHA_CHARS = "ABCDEFGHJKMNPQRTUVWXY346789";
    private static final int CODE_LENGTH = 4;
    private static final int WIDTH = 140;
    private static final int HEIGHT = 52;
    /** 字符下方彩色干扰线数量：提升干扰度，浅灰细线不遮挡深色字符 */
    private static final int LINE_COUNT = 6;
    /** 压字符的深色干扰线数量：取自字符同色池，破坏按颜色分割字符的 OCR 预处理 */
    private static final int DARK_OVER_LINE_COUNT = 3;
    /** 背景噪点数量：随机彩色小点，增加背景纹理 */
    private static final int DOT_COUNT = 40;
    /** 前景噪点数量：撒在字符上方的浅色小点，干扰字符轮廓提取 */
    private static final int FG_DOT_COUNT = 30;
    /** 彩色弧线数量：位于字符下方，增加花花绿绿的干扰纹理 */
    private static final int ARC_COUNT = 3;
    /** 单字符渲染缓冲区宽度（> 最大字宽，容纳旋转/扭曲溢出） */
    private static final int CELL_W = 34;
    /** 字符颜色池：高饱和深色系，与白底对比度高，保证人眼可读 */
    private static final Color[] CHAR_COLORS = {
            new Color(178, 34, 34),   // 火砖红
            new Color(25, 90, 60),    // 墨绿
            new Color(30, 60, 160),   // 宝蓝
            new Color(120, 40, 140),  // 紫
            new Color(180, 90, 20),   // 橙棕
            new Color(20, 120, 130),  // 青
            new Color(160, 30, 90),   // 品红
            new Color(90, 60, 20)     // 棕
    };
    /** 干扰元素颜色池：中等明度彩色，足够花哨又不至于压过深色字符 */
    private static final Color[] NOISE_COLORS = {
            new Color(220, 120, 120), new Color(120, 180, 120),
            new Color(120, 150, 220), new Color(200, 160, 90),
            new Color(170, 120, 200), new Color(90, 190, 190),
            new Color(230, 150, 170), new Color(150, 170, 90)
    };
    /** 清晰的逻辑字体（headless 环境下始终可解析），加粗提升可读性 */
    private static final Font CAPTCHA_FONT = new Font("SansSerif", Font.BOLD, 30);

    static {
        System.setProperty("java.awt.headless", "true");
    }

    private final RedisUtil redisUtil;

    public CaptchaService(RedisUtil redisUtil) {
        this.redisUtil = redisUtil;
    }

    public CaptchaVO generate() {
        ReadableLineCaptcha captcha = new ReadableLineCaptcha(
                WIDTH, HEIGHT, new RandomGenerator(CAPTCHA_CHARS, CODE_LENGTH), LINE_COUNT);
        captcha.createCode();
        String key = IdUtil.fastSimpleUUID();
        redisUtil.set(RedisKeyConstants.CAPTCHA_PREFIX + key, captcha.getCode(), TTL);
        return new CaptchaVO(key, captcha.getImageBase64Data());
    }

    /** 校验成功返回 true 并销毁；失败计数，同一验证码错 3 次即销毁（防对同一码反复枚举/OCR 多轮投票） */
    public boolean verify(String key, String code) {
        if (key == null || code == null) return false;
        String k = RedisKeyConstants.CAPTCHA_PREFIX + key;
        String stored = redisUtil.get(k);
        if (stored == null) return false;
        boolean ok = stored.equalsIgnoreCase(code);
        if (ok) {
            redisUtil.delete(k);
            redisUtil.delete(k + ":fails");
            return true;
        }
        long fails = redisUtil.incr(k + ":fails", TTL);
        if (fails >= 3) {
            redisUtil.delete(k);
            redisUtil.delete(k + ":fails");
            log.warn("验证码失败次数超限销毁: fails={}", fails);
        }
        return false;
    }

    /**
     * 自定义渲染的抗 OCR 验证码：纯白底 + 彩色背景纹理，逐字符独立渲染
     * （随机字号/深色/大角度旋转）后做正弦波形扭曲，再以「粘连」间距贴合，
     * 最后压上与字符同色的深色干扰线——破坏二值化、颜色过滤与字符切分
     * 三类 OCR 预处理，人眼仍可读。
     */
    static class ReadableLineCaptcha extends LineCaptcha {

        ReadableLineCaptcha(int width, int height, CodeGenerator generator, int interfereCount) {
            super(width, height, generator, interfereCount);
        }

        @Override
        public BufferedImage createImage(String code) {
            BufferedImage image = new BufferedImage(this.width, this.height, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = image.createGraphics();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

                // 纯白底
                g.setColor(Color.WHITE);
                g.fillRect(0, 0, this.width, this.height);

                ThreadLocalRandom rnd = ThreadLocalRandom.current();

                // 背景干扰：彩色细线 + 弧线 + 噪点（先画，位于字符之下）
                g.setStroke(new BasicStroke(1f));
                int lines = Math.max(0, this.interfereCount);
                for (int i = 0; i < lines; i++) {
                    g.setColor(NOISE_COLORS[rnd.nextInt(NOISE_COLORS.length)]);
                    g.drawLine(rnd.nextInt(this.width), rnd.nextInt(this.height),
                            rnd.nextInt(this.width), rnd.nextInt(this.height));
                }
                for (int i = 0; i < ARC_COUNT; i++) {
                    g.setColor(NOISE_COLORS[rnd.nextInt(NOISE_COLORS.length)]);
                    g.drawArc(rnd.nextInt(this.width / 2), rnd.nextInt(this.height / 2),
                            this.width / 2 + rnd.nextInt(this.width / 2),
                            this.height / 2 + rnd.nextInt(this.height),
                            rnd.nextInt(360), 60 + rnd.nextInt(120));
                }
                for (int i = 0; i < DOT_COUNT; i++) {
                    g.setColor(NOISE_COLORS[rnd.nextInt(NOISE_COLORS.length)]);
                    g.fillRect(rnd.nextInt(this.width), rnd.nextInt(this.height), 2, 2);
                }

                // 逐字符：独立缓冲 → 随机字号/深色/旋转 → 波形扭曲 → 粘连贴合。
                // advance < CELL_W 使相邻字符边缘交叠，OCR 难以按空白列切分
                int len = code.length();
                int advance = CELL_W - 8 + rnd.nextInt(3);            // 字距 26~28，粘连 6~8px
                int totalW = advance * (len - 1) + CELL_W;
                int startX = Math.max(0, (this.width - totalW) / 2);
                for (int i = 0; i < len; i++) {
                    String ch = String.valueOf(code.charAt(i));
                    BufferedImage buf = new BufferedImage(CELL_W, this.height, BufferedImage.TYPE_INT_ARGB);
                    Graphics2D gb = buf.createGraphics();
                    try {
                        gb.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                        gb.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                        gb.setFont(CAPTCHA_FONT.deriveFont((float) (27 + rnd.nextInt(6))));   // 27~32 随机字号
                        gb.setColor(CHAR_COLORS[rnd.nextInt(CHAR_COLORS.length)]);
                        FontMetrics fm = gb.getFontMetrics();
                        int cw = fm.stringWidth(ch);
                        int cy = (this.height - fm.getHeight()) / 2 + fm.getAscent();
                        gb.rotate(Math.toRadians(rnd.nextInt(57) - 28), CELL_W / 2.0, this.height / 2.0);
                        gb.drawString(ch, (CELL_W - cw) / 2, cy);
                    } finally {
                        gb.dispose();
                    }
                    // 正弦波形扭曲：逐列垂直位移，破坏字符基线与骨架提取
                    BufferedImage warped = waveDistort(buf,
                            2.5 + rnd.nextDouble() * 1.5,           // 幅度 2.5~4px
                            20 + rnd.nextInt(12),                   // 周期 20~31px
                            rnd.nextDouble() * Math.PI * 2);        // 随机相位
                    g.drawImage(warped, startX + i * advance, rnd.nextInt(5) - 2, null);
                }

                // 深色干扰线压字符：与字符同色池，使按颜色聚类分离字符失效
                for (int i = 0; i < DARK_OVER_LINE_COUNT; i++) {
                    g.setColor(CHAR_COLORS[rnd.nextInt(CHAR_COLORS.length)]);
                    g.setStroke(new BasicStroke(1.2f + rnd.nextFloat() * 0.6f));
                    g.drawLine(rnd.nextInt(this.width), 10 + rnd.nextInt(this.height - 20),
                            rnd.nextInt(this.width), 10 + rnd.nextInt(this.height - 20));
                }
                // 前景浅色噪点：干扰字符轮廓
                for (int i = 0; i < FG_DOT_COUNT; i++) {
                    g.setColor(NOISE_COLORS[rnd.nextInt(NOISE_COLORS.length)]);
                    g.fillRect(rnd.nextInt(this.width), rnd.nextInt(this.height), 1 + rnd.nextInt(2), 1 + rnd.nextInt(2));
                }
            } finally {
                g.dispose();
            }
            return image;
        }

        /** 正弦波形扭曲：逐列垂直位移（透明像素随列搬运），抗字符骨架/基线提取 */
        private static BufferedImage waveDistort(BufferedImage src, double amp, double period, double phase) {
            int w = src.getWidth(), h = src.getHeight();
            BufferedImage dst = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            for (int x = 0; x < w; x++) {
                int dy = (int) Math.round(amp * Math.sin(2 * Math.PI * x / period + phase));
                for (int y = 0; y < h; y++) {
                    int ny = y + dy;
                    if (ny >= 0 && ny < h) {
                        dst.setRGB(x, ny, src.getRGB(x, y));
                    }
                }
            }
            return dst;
        }
    }

    @Data
    public static class CaptchaVO {
        private final String captchaKey;
        private final String captchaImg;
    }
}
