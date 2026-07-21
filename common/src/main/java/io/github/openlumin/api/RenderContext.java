package io.github.openlumin.api;

/**
 * 渲染上下文抽象接口
 * 封装窗口状态、矩阵栈、GUI 缩放等版本相关的渲染环境
 */
public interface RenderContext {

    /** 获取 GUI 缩放因子 */
    double getGuiScale();

    /** 获取缩放后的屏幕宽度 */
    float getScaledWidth();

    /** 获取缩放后的屏幕高度 */
    float getScaledHeight();

    /** 获取原始帧缓冲宽度（像素） */
    int getFramebufferWidth();

    /** 获取原始帧缓冲高度（像素） */
    int getFramebufferHeight();

    /** 推送矩阵（进入新坐标空间） */
    void pushMatrix();

    /** 弹出矩阵（恢复上一个坐标空间） */
    void popMatrix();

    /** 平移当前矩阵 */
    void translate(double x, double y, double z);

    /** 缩放当前矩阵 */
    void scale(double x, double y, double z);

    /** 旋转当前矩阵（角度制） */
    void rotate(float angle, float x, float y, float z);
}
