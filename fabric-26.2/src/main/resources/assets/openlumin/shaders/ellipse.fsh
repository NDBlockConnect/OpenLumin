#version 410 core

in vec2 f_Position;
in vec4 f_Color;
in vec4 f_InnerRect;
in vec4 f_Radius;

layout(location = 0) out vec4 fragColor;

// Inigo Quilez 椭圆有向距离近似式。
// 对基于 fwidth 的抗锯齿足够精确，避免精确解的迭代开销。
// ab = 半轴 (a, b)，返回值内部为负、边界为 0、外部为正。
float sdEllipse(vec2 p, vec2 ab) {
    // 防止半轴为 0 造成除零
    ab = max(ab, vec2(1e-4));
    float k1 = length(p / ab);
    float k2 = length(p / (ab * ab));
    if (k2 < 1e-6) {
        // p 位于中心，直接返回负的最小半轴
        return -min(ab.x, ab.y);
    }
    return k1 * (k1 - 1.0) / k2;
}

void main() {
    vec2 halfSize = (f_InnerRect.zw - f_InnerRect.xy) * 0.5;
    vec2 center = (f_InnerRect.xy + f_InnerRect.zw) * 0.5;
    vec2 p = f_Position - center;

    float dist = sdEllipse(p, halfSize);

    float delta = fwidth(dist);
    float alpha = 1.0 - smoothstep(-delta, delta, dist);

    fragColor = vec4(f_Color.rgb, f_Color.a * alpha);
    if (alpha < 0.001) discard;
}
