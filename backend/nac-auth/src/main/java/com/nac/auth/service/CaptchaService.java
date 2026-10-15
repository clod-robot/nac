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
    private static final int WIDTH = 130;
    private static final int HEIGHT = 48;
    /** 字符下方干扰线数量：提升干扰度，浅灰细线不遮挡深色字符 */
    private static final int LINE_COUNT = 5;
    /** 字符上方干扰线数量：1 条细线压过字符，增强干扰而不影响辨认 */
    private static final int OVER_LINE_COUNT = 1;
    /** 噪点数量：随机彩色小点，增加背景纹理 */
    private static final int DOT_COUNT = 24;
    /** 彩色弧线数量：位于字符下方，增加花花绿绿的干扰纹理 */
    private static final int ARC_COUNT = 2;
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

    /** 校验成功返回 true 并销毁；失败返回 false（不销毁，允许有限重试由调用方控制） */
    public boolean verify(String key, String code) {
        if (key == null || code == null) return false;
        String stored = redisUtil.get(RedisKeyConstants.CAPTCHA_PREFIX + key);
        if (stored == null) return false;
        boolean ok = stored.equalsIgnoreCase(code);
        if (ok) {
            redisUtil.delete(RedisKeyConstants.CAPTCHA_PREFIX + key);
        }
        return ok;
    }

    /**
     * 自定义渲染的彩色干扰验证码：纯白底、彩色干扰线/噪点/弧线、
     * 逐字随机高饱和深色加粗字符、不透明、轻微位移 + 小角度旋转。
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

                // 彩色干扰线 + 噪点 + 弧线（先画，位于字符之下，不遮挡字符）
                g.setStroke(new BasicStroke(1f));
                int lines = Math.max(0, this.interfereCount);
                for (int i = 0; i < lines; i++) {
                    g.setColor(NOISE_COLORS[rnd.nextInt(NOISE_COLORS.length)]);
                    g.drawLine(rnd.nextInt(this.width), rnd.nextInt(this.height),
                            rnd.nextInt(this.width), rnd.nextInt(this.height));
                }
                for (int i = 0; i < DOT_COUNT; i++) {
                    g.setColor(NOISE_COLORS[rnd.nextInt(NOISE_COLORS.length)]);
                    g.fillRect(rnd.nextInt(this.width), rnd.nextInt(this.height), 2, 2);
                }
                // 彩色弧线：进一步增加纹理复杂度
                for (int i = 0; i < ARC_COUNT; i++) {
                    g.setColor(NOISE_COLORS[rnd.nextInt(NOISE_COLORS.length)]);
                    g.drawArc(rnd.nextInt(this.width / 2), rnd.nextInt(this.height / 2),
                            this.width / 2 + rnd.nextInt(this.width / 2),
                            this.height / 2 + rnd.nextInt(this.height),
                            rnd.nextInt(360), 60 + rnd.nextInt(120));
                }

                // 彩色加粗字符：逐字随机深色 + 轻微位移 + 小角度旋转（±18°），
                // 人眼易读但显著提升机器 OCR 切分与识别难度
                g.setFont(CAPTCHA_FONT);
                FontMetrics fm = g.getFontMetrics();
                int len = code.length();
                int cell = this.width / (len + 1);
                int baseY = (this.height - fm.getHeight()) / 2 + fm.getAscent();
                for (int i = 0; i < len; i++) {
                    String ch = String.valueOf(code.charAt(i));
                    int cw = fm.stringWidth(ch);
                    int x = cell * (i + 1) - cw / 2 + rnd.nextInt(5) - 2;
                    int y = baseY + rnd.nextInt(5) - 2;
                    Graphics2D gc = (Graphics2D) g.create();
                    try {
                        gc.setColor(CHAR_COLORS[rnd.nextInt(CHAR_COLORS.length)]);
                        gc.rotate(Math.toRadians(rnd.nextInt(37) - 18),
                                x + cw / 2.0, y - fm.getAscent() / 2.0);
                        gc.drawString(ch, x, y);
                    } finally {
                        gc.dispose();
                    }
                }

                // 1 条彩色细线压在字符上方，增强干扰但深色字符仍清晰可辨
                for (int i = 0; i < OVER_LINE_COUNT; i++) {
                    g.setColor(NOISE_COLORS[rnd.nextInt(NOISE_COLORS.length)]);
                    g.drawLine(rnd.nextInt(this.width), rnd.nextInt(this.height),
                            rnd.nextInt(this.width), rnd.nextInt(this.height));
                }
            } finally {
                g.dispose();
            }
            return image;
        }
    }

    @Data
    public static class CaptchaVO {
        private final String captchaKey;
        private final String captchaImg;
    }
}
