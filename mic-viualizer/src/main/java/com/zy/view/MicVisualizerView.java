package com.zy.view;

import android.content.Context;
import android.graphics.*;
import android.util.AttributeSet;
import android.view.View;
import androidx.annotation.Nullable;
import java.util.LinkedList;
import java.util.Random;

/**
 * ==========================================================
 *  高性能麦克风音量可视化组件（GPU优化版 + 注释版 + 自适应尺寸修复）
 * ==========================================================
 */
public class MicVisualizerView extends View {

    // ======================== 基础绘图工具 ========================
    private final Paint circlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint particlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    // ======================== 音量控制参数 ========================
    private float displayRms = 0f;
    private float targetRms = 0f;
    private float maxRms = 2000f;
    private static final float MIN_RMS = 50f;

    // ======================== 波纹动画参数 ========================
    private static final long RIPPLE_DURATION_MS = 1500L;
    private static final float RIPPLE_START_SCALE = 0.35f;
    private static final float RIPPLE_END_SCALE = 1.6f;
    private static final long IDLE_RIPPLE_INTERVAL_MS = 1400L;
    private static final long ACTIVE_RIPPLE_MIN_INTERVAL_MS = 280L;

    private long lastRippleSpawnMs = 0L;
    private final LinkedList<Ripple> ripples = new LinkedList<>();

    private static class Ripple {
        final long startTimeMs;
        Ripple(long t) { this.startTimeMs = t; }
    }

    // ======================== 呼吸动画状态 ========================
    private boolean isBreathing = true;
    private long lastSoundTimeMs = 0L;
    private static final long SOUND_TIMEOUT_MS = 1000L;
    private static final long BREATH_INTERVAL_MS = 2000L;

    // ======================== 粒子系统参数 ========================
    private static final int PARTICLE_COUNT = 14;
    private final Random random = new Random();
    private final Particle[] particles = new Particle[PARTICLE_COUNT];

    private static class Particle {
        float angle, speed, distance, size, brightness;
        boolean active;
        final float[] trailX, trailY, trailAlpha;
        final int trailLength;
        int head = 0;
        boolean full = false;
        private static final int MIN_TRAIL = 12, MAX_TRAIL = 24;

        Particle(Random rand) {
            this.trailLength = MIN_TRAIL + rand.nextInt(MAX_TRAIL - MIN_TRAIL + 1);
            this.trailX = new float[trailLength];
            this.trailY = new float[trailLength];
            this.trailAlpha = new float[trailLength];
        }

        void updateTrail(float x, float y, float brightness) {
            trailX[head] = x;
            trailY[head] = y;
            trailAlpha[head] = brightness;
            head = (head + 1) % trailLength;
            full |= (head == 0);
            int size = full ? trailLength : head;
            int start = full ? head : 0;
            for (int i = 1; i < size; i++) {
                int idx = (start + i) % trailLength;
                trailAlpha[idx] *= 0.94f;
            }
        }

        int size() { return full ? trailLength : head; }
        int startIndex() { return full ? head : 0; }
    }

    // ======================== 颜色定义 ========================
    private static final int COLOR_CORE = Color.parseColor("#448AFF");
    private static final int COLOR_EDGE = Color.parseColor("#448AFF");
    private static final int COLOR_DEEP_BLUE = Color.parseColor("#1A237E");

    // ======================== 中心渐变缓存 ========================
    private RadialGradient centerGradient;
    private float gradientRadius = -1f;

    // ======================== 自适应尺寸参数 ========================
    private float minDim = 300f;       // 当前最小边长（px）
    private float scaleFactor = 1f;    // 缩放因子（基于 300px 基准）

    // ======================== 动画刷新任务（约60fps） ========================
    private final Runnable updateRunnable = new Runnable() {
        @Override
        public void run() {
            updateState();
            invalidate();
            postDelayed(this, 16);
        }
    };

    // ======================== 构造函数 ========================
    public MicVisualizerView(Context context) {
        super(context);
        init();
    }

    public MicVisualizerView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    /** 初始化绘制参数与粒子系统 */
    private void init() {
        circlePaint.setStyle(Paint.Style.FILL);
        particlePaint.setStyle(Paint.Style.FILL);

        // 使用默认尺寸初始化粒子（后续会在 onSizeChanged 中修正）
        float defaultMinDim = 300f;
        for (int i = 0; i < PARTICLE_COUNT; i++) {
            particles[i] = new Particle(random);
            resetParticle(particles[i], defaultMinDim);
        }

        post(updateRunnable);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        minDim = Math.min(w, h);
        scaleFactor = minDim / 300f;  // 300px ≈ 150dp @2x

        // 尺寸变化时重新初始化所有粒子
        for (Particle p : particles) {
            resetParticle(p, minDim);
        }
    }

    /** 重置粒子属性，基于当前 View 尺寸自适应 */
    private void resetParticle(Particle p, float minDim) {
        p.angle = random.nextFloat() * (float) (Math.PI * 2);

        // 轨道半径：约占中心区域 33% ± 23%
        float baseDistance = minDim * 0.33f;
        float variance = minDim * 0.23f;
        p.distance = baseDistance + random.nextFloat() * variance;

        // 速度：大屏幕稍快
        p.speed = (0.015f + random.nextFloat() * 0.03f) * (0.8f + 0.4f * scaleFactor);

        // 粒子大小：按比例缩放
        float baseSize = minDim * 0.005f;
        p.size = baseSize * (1.0f + random.nextFloat() * 1.3f);

        p.brightness = 0.35f + random.nextFloat() * 0.65f;
        p.active = true;
        p.full = false;
        p.head = 0;
    }

    // ======================== 音量输入接口 ========================
    public void setRmsLevel(float rms) { targetRms = rms; }
    public void setMaxRms(float maxRms) { this.maxRms = maxRms; }

    // ======================== 状态更新逻辑 ========================
    private void updateState() {
        long now = System.currentTimeMillis();

        // 平滑 RMS
        float clamped = Math.max(0f, Math.min(targetRms, maxRms));
        displayRms = displayRms * 0.85f + clamped * 0.15f;
        float normalized = maxRms > 0 ? displayRms / maxRms : 0f;

        if (clamped > MIN_RMS) lastSoundTimeMs = now;
        isBreathing = (now - lastSoundTimeMs) > SOUND_TIMEOUT_MS;

        // 波纹生成
        boolean active = normalized > 0.10f;
        long interval = active ? ACTIVE_RIPPLE_MIN_INTERVAL_MS : IDLE_RIPPLE_INTERVAL_MS;
        if (now - lastRippleSpawnMs >= interval) spawnRipple(now);

        while (!ripples.isEmpty() && now - ripples.peekFirst().startTimeMs > RIPPLE_DURATION_MS) {
            ripples.removeFirst();
        }

        // 更新粒子
        float speedFactor = isBreathing ? 0.5f : (0.8f + normalized * 2.0f);
        float baseBrightness = 0.4f + normalized * 0.6f;
        float expansion = normalized * (minDim * 0.15f); // 音量膨胀

        for (Particle p : particles) {
            if (!p.active) continue;
            p.angle = (p.angle + p.speed * speedFactor) % ((float) (Math.PI * 2.0));

            // 随机扰动 + 音量膨胀
            float targetDist = p.distance + (random.nextFloat() - 0.5f) * 0.6f * scaleFactor;
            p.distance = clamp(targetDist + expansion, minDim * 0.2f, minDim * 0.6f);

            float depth = Math.sin(p.angle) >= 0 ? 1.0f : 0.6f;
            p.brightness = clamp(baseBrightness * depth * (0.8f + 0.2f * random.nextFloat()), 0.15f, 1f);
        }
    }

    private void spawnRipple(long now) {
        ripples.add(new Ripple(now));
        lastRippleSpawnMs = now;
        if (ripples.size() > 4) ripples.removeFirst();
    }

    // ======================== 绘制逻辑 ========================
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int w = getWidth(), h = getHeight();
        int cx = w / 2, cy = h / 2;

        float normalized = Math.min(displayRms / maxRms, 1f);
        float baseRadius = minDim * 0.16f;

        // 呼吸节奏
        float breathPhase = (float) ((System.currentTimeMillis() % BREATH_INTERVAL_MS) /
                (double) BREATH_INTERVAL_MS * 2 * Math.PI);
        float breathing = isBreathing ? (0.02f * (float) Math.sin(breathPhase)) : 0f;
        float centerScale = 0.4f * normalized + 0.06f + breathing;
        float sphereRadius = baseRadius * (1.0f + centerScale);

        drawParticles(canvas, cx, cy, false);
        drawCenterSphere(canvas, cx, cy, sphereRadius);
        drawParticles(canvas, cx, cy, true);
        drawRipples(canvas, cx, cy, normalized, baseRadius, minDim);
    }

    private void drawParticles(Canvas canvas, int cx, int cy, boolean frontHalf) {
        for (Particle p : particles) {
            if (!p.active) continue;
            boolean front = (p.angle % (Math.PI * 2)) < Math.PI;
            if (front == frontHalf) drawParticle(canvas, p, cx, cy);
        }
    }

    private void drawParticle(Canvas canvas, Particle p, float cx, float cy) {
        float x = cx + (float) Math.cos(p.angle) * p.distance;
        float y = cy + (float) Math.sin(p.angle) * p.distance * 0.4f;

        p.updateTrail(x, y, p.brightness);
        int start = p.startIndex(), count = p.size();

        for (int k = 0; k < count; k++) {
            int idx = (start + k) % p.trailLength;
            float alphaF = clamp(p.trailAlpha[idx], 0f, 1f);
            particlePaint.setColor(Color.argb((int) (alphaF * 255), 68, 138, 255));
            float size = p.size * (0.3f + k * 0.15f) * 0.7f;
            canvas.drawCircle(p.trailX[idx], p.trailY[idx], size, particlePaint);
        }

        particlePaint.setColor(COLOR_CORE);
        particlePaint.setAlpha((int) (p.brightness * 255));
        canvas.drawCircle(x, y, p.size * 1.3f, particlePaint);
    }

    private void drawCenterSphere(Canvas canvas, float cx, float cy, float radius) {
        if (centerGradient == null || Math.abs(gradientRadius - radius) > 0.5f) {
            centerGradient = new RadialGradient(
                    cx, cy, radius,
                    new int[]{
                            Color.argb(230, 0, 255, 255),
                            COLOR_EDGE,
                            COLOR_DEEP_BLUE
                    },
                    new float[]{0f, 0.7f, 1f},
                    Shader.TileMode.CLAMP
            );
            gradientRadius = radius;
        }
        circlePaint.setShader(centerGradient);
        canvas.drawCircle(cx, cy, radius, circlePaint);
    }

    private void drawRipples(Canvas canvas, int cx, int cy, float normalized, float baseRadius, float minDim) {
        float maxRadius = minDim * 0.50f;
        int waveColor = blendColor(COLOR_EDGE, Color.WHITE, normalized * 0.7f);

        for (Ripple r : ripples) {
            float t = clamp((System.currentTimeMillis() - r.startTimeMs) /
                    (float) RIPPLE_DURATION_MS, 0f, 1f);
            float scale = RIPPLE_START_SCALE + t * (RIPPLE_END_SCALE - RIPPLE_START_SCALE);
            float radius = baseRadius * (1f + scale) + normalized * maxRadius * 0.15f;
            int alpha = (int) (180 * (1f - t));

            RadialGradient gradient = new RadialGradient(
                    cx, cy, radius,
                    new int[]{Color.TRANSPARENT,
                            Color.argb(alpha, Color.red(waveColor),
                                    Color.green(waveColor), Color.blue(waveColor)),
                            Color.TRANSPARENT},
                    new float[]{0f, 0.7f, 1f},
                    Shader.TileMode.CLAMP
            );
            circlePaint.setShader(gradient);
            canvas.drawCircle(cx, cy, radius, circlePaint);
        }
    }

    // ======================== 工具函数 ========================
    private float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }

    private int blendColor(int c1, int c2, float ratio) {
        ratio = clamp(ratio, 0f, 1f);
        return Color.argb(
                (int) (Color.alpha(c1) + (Color.alpha(c2) - Color.alpha(c1)) * ratio),
                (int) (Color.red(c1) + (Color.red(c2) - Color.red(c1)) * ratio),
                (int) (Color.green(c1) + (Color.green(c2) - Color.green(c1)) * ratio),
                (int) (Color.blue(c1) + (Color.blue(c2) - Color.blue(c1)) * ratio)
        );
    }
}
