#version 410 core

in vec2 f_Position;
in vec4 f_Color;
in vec4 f_InnerRect;
in vec4 f_Radius; // x=startAngle, y=endAngle, z=innerRatio, w=spare（弧度制）

layout(location = 0) out vec4 fragColor;

const float PI = 3.14159265359;

// Inigo Quilez 椭圆有向距离近似式（与 ellipse.fsh 一致）。
float sdEllipse(vec2 p, vec2 ab) {
    ab = max(ab, vec2(1e-4));
    float k1 = length(p / ab);
    float k2 = length(p / (ab * ab));
    if (k2 < 1e-6) {
        return -min(ab.x, ab.y);
    }
    return k1 * (k1 - 1.0) / k2;
}

void main() {
    vec2 halfSize = (f_InnerRect.zw - f_InnerRect.xy) * 0.5;
    vec2 center = (f_InnerRect.xy + f_InnerRect.zw) * 0.5;
    vec2 p = f_Position - center;

    float startAngle = f_Radius.x;
    float endAngle = f_Radius.y;
    float innerRatio = clamp(f_Radius.z, 0.0, 1.0);

    // 环带：位于外椭圆内 且 内椭圆外。ringDist < 0 表示在环带内。
    // innerRatio = 0 时内椭圆退化，环带变为实心扇形（pie）。
    float sdOuter = sdEllipse(p, halfSize);
    float sdInner = sdEllipse(p, halfSize * innerRatio);
    float ringDist = max(sdOuter, -sdInner);

    // 角度掩码：以中线 mid 为基准，判断点角度与 mid 的偏差是否在半扫掠角内。
    float mid = (startAngle + endAngle) * 0.5;
    float halfSweep = (endAngle - startAngle) * 0.5;
    float angle = atan(p.y, p.x);
    float d = angle - mid;
    d = atan(sin(d), cos(d)); // 归一化到 -PI..PI，正确处理环绕

    float r = length(p);
    float angEdge;
    if (halfSweep >= PI - 0.001) {
        // 整圆：无角度裁剪，退化为完整椭圆环
        angEdge = 1e6;
    } else {
        // (halfSweep - |d|) > 0 表示在扇形角度范围内；乘半径近似为像素距离，供 fwidth 抗锯齿
        angEdge = (halfSweep - abs(d)) * max(r, 1.0);
    }

    // 环带内(<0) 且 角度内(angEdge>0 → -angEdge<0)，取 max 得到弧段有向距离
    float dist = max(ringDist, -angEdge);

    float delta = fwidth(dist);
    float alpha = 1.0 - smoothstep(-delta, delta, dist);

    fragColor = vec4(f_Color.rgb, f_Color.a * alpha);
    if (alpha < 0.001) discard;
}
