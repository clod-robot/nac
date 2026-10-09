package com.his.auth.service;

import com.his.common.constant.RedisKeyConstants;
import com.his.common.redis.RedisUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.Random;
import java.util.UUID;

/**
 * 图形验证码：生成后存 Redis（key=captcha:uuid），验证后立即删除防重放。
 */
@Service
public class CaptchaService {

    @Autowired
    private RedisUtil redisUtil;

    private static final int WIDTH = 120, HEIGHT = 40, CODE_LEN = 4;
    private static final String CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private final Random random = new Random();

    /** 返回 { captchaKey, image(base64) } */
    public String[] generate() {
        StringBuilder code = new StringBuilder();
        for (int i = 0; i < CODE_LEN; i++) code.append(CHARS.charAt(random.nextInt(CHARS.length())));
        String key = UUID.randomUUID().toString().replace("-", "");
        redisUtil.set(RedisKeyConstants.CAPTCHA_PREFIX + key, code.toString(), 300);

        BufferedImage img = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, WIDTH, HEIGHT);
        g.setFont(new Font("Arial", Font.BOLD, 28));
        for (int i = 0; i < code.length(); i++) {
            g.setColor(new Color(random.nextInt(120), random.nextInt(120), random.nextInt(120)));
            g.drawString(String.valueOf(code.charAt(i)), 20 + i * 24, 30);
        }
        // 干扰线
        for (int i = 0; i < 6; i++) {
            g.setColor(new Color(random.nextInt(200), random.nextInt(200), random.nextInt(200)));
            g.drawLine(random.nextInt(WIDTH), random.nextInt(HEIGHT), random.nextInt(WIDTH), random.nextInt(HEIGHT));
        }
        g.dispose();
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            ImageIO.write(img, "png", bos);
            String base64 = "data:image/png;base64," + Base64.getEncoder().encodeToString(bos.toByteArray());
            return new String[]{key, base64};
        } catch (IOException e) {
            throw new RuntimeException("验证码生成失败", e);
        }
    }

    /** 校验成功后删除（一次性，防重放） */
    public boolean verify(String key, String input) {
        if (key == null || input == null) return false;
        String redisKey = RedisKeyConstants.CAPTCHA_PREFIX + key;
        Object cached = redisUtil.get(redisKey);
        if (cached == null) return false;
        boolean ok = cached.toString().equalsIgnoreCase(input.trim());
        redisUtil.delete(redisKey);
        return ok;
    }
}
