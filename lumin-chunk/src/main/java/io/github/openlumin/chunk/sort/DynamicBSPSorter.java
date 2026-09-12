package io.github.openlumin.chunk.sort;

import java.util.ArrayList;
import java.util.List;

/**
 * 动态 BSP 排序（Sodium InnerPartitionBSPNode 语义参照）：二叉树分区 + 相机侧序遍历。
 * <p>分区面选择 = 离中央最近的四边形的面平面；全前/全后/跨越判定基于 4 角点评估。
 * 跨越四边形存储于分区节点自身（不剖切，剖切/分裂方案列后续优化）。
 * 树在 {@code sort} 调用时构建（回避相机无关的重建时机复杂度）。</p>
 */
public final class DynamicBSPSorter implements LuminTranslucentSorter {

    @Override
    public LuminSortStrategy strategy() {
        return LuminSortStrategy.DYNAMIC_BSP;
    }

    @Override
    public List<LuminTranslucentQuad> sort(LuminCameraState camera, List<LuminTranslucentQuad> quads) {
        if (quads.isEmpty()) {
            return new ArrayList<>();
        }
        Node root = Node.build(new ArrayList<>(quads));
        List<LuminTranslucentQuad> result = new ArrayList<>(quads.size());
        root.traverse(camera.localX(), camera.localY(), camera.localZ(), result);
        return result;
    }

    @Override
    public void close() {
    }

    static final class LeafNode extends Node {
        private final List<LuminTranslucentQuad> quads;

        LeafNode(List<LuminTranslucentQuad> quads) {
            this.quads = quads;
        }

        @Override
        void traverse(float cx, float cy, float cz, List<LuminTranslucentQuad> out) {
            out.addAll(quads);
        }
    }

    static final class InnerNode extends Node {
        private final float nx, ny, nz, d;
        private final List<LuminTranslucentQuad> spanning;
        private final Node back;
        private final Node front;

        InnerNode(float nx, float ny, float nz, float d, List<LuminTranslucentQuad> spanning, Node back, Node front) {
            this.nx = nx;
            this.ny = ny;
            this.nz = nz;
            this.d = d;
            this.spanning = spanning;
            this.back = back;
            this.front = front;
        }

        @Override
        void traverse(float cx, float cy, float cz, List<LuminTranslucentQuad> out) {
            float camSide = nx * cx + ny * cy + nz * cz + d;
            if (camSide > 0) {
                back.traverse(cx, cy, cz, out);
                out.addAll(spanning);
                front.traverse(cx, cy, cz, out);
            } else {
                front.traverse(cx, cy, cz, out);
                out.addAll(spanning);
                back.traverse(cx, cy, cz, out);
            }
        }
    }

    static abstract sealed class Node permits LeafNode, InnerNode {
        // Keep single-quad leaves so small sections still exercise BSP ordering.
        // A larger threshold would silently turn the common two-quad case into identity order.
        static final int LEAF_THRESHOLD = 1;

        abstract void traverse(float cx, float cy, float cz, List<LuminTranslucentQuad> out);

        static Node build(List<LuminTranslucentQuad> quads) {
            if (quads.size() <= LEAF_THRESHOLD) {
                return new LeafNode(quads);
            }
            LuminTranslucentQuad partitioner = pickPartition(quads);
            float nx = partitioner.planeNX();
            float ny = partitioner.planeNY();
            float nz = partitioner.planeNZ();
            float d = partitioner.planeD();

            List<LuminTranslucentQuad> front = new ArrayList<>();
            List<LuminTranslucentQuad> back = new ArrayList<>();
            List<LuminTranslucentQuad> spanning = new ArrayList<>();

            for (LuminTranslucentQuad q : quads) {
                Classification c = classify(q, nx, ny, nz, d);
                switch (c) {
                    case FRONT -> front.add(q);
                    case BACK -> back.add(q);
                    case SPANNING -> spanning.add(q);
                }
            }
            Node frontNode = front.isEmpty() ? new LeafNode(front) : build(front);
            Node backNode = back.isEmpty() ? new LeafNode(back) : build(back);
            return new InnerNode(nx, ny, nz, d, spanning, backNode, frontNode);
        }

        private static LuminTranslucentQuad pickPartition(List<LuminTranslucentQuad> quads) {
            double centerX = 0, centerY = 0, centerZ = 0;
            for (LuminTranslucentQuad q : quads) {
                centerX += q.corner0X() + q.corner1X() + q.corner2X() + q.corner3X();
                centerY += q.corner0Y() + q.corner1Y() + q.corner2Y() + q.corner3Y();
                centerZ += q.corner0Z() + q.corner1Z() + q.corner2Z() + q.corner3Z();
            }
            int n = quads.size() * 4;
            centerX /= n;
            centerY /= n;
            centerZ /= n;
            LuminTranslucentQuad best = quads.get(0);
            double bestDist = Double.MAX_VALUE;
            for (LuminTranslucentQuad q : quads) {
                double qx = (q.corner0X() + q.corner1X() + q.corner2X() + q.corner3X()) * 0.25;
                double qy = (q.corner0Y() + q.corner1Y() + q.corner2Y() + q.corner3Y()) * 0.25;
                double qz = (q.corner0Z() + q.corner1Z() + q.corner2Z() + q.corner3Z()) * 0.25;
                double dist = (qx - centerX) * (qx - centerX) + (qy - centerY) * (qy - centerY) + (qz - centerZ) * (qz - centerZ);
                if (dist < bestDist) {
                    bestDist = dist;
                    best = q;
                }
            }
            return best;
        }

        enum Classification {FRONT, BACK, SPANNING}

        private static Classification classify(LuminTranslucentQuad q, float px, float py, float pz, float pd) {
            float d0 = px * q.corner0X() + py * q.corner0Y() + pz * q.corner0Z() + pd;
            float d1 = px * q.corner1X() + py * q.corner1Y() + pz * q.corner1Z() + pd;
            float d2 = px * q.corner2X() + py * q.corner2Y() + pz * q.corner2Z() + pd;
            float d3 = px * q.corner3X() + py * q.corner3Y() + pz * q.corner3Z() + pd;
            float eps = 1e-6f;
            boolean front0 = d0 > eps, back0 = d0 < -eps;
            boolean front1 = d1 > eps, back1 = d1 < -eps;
            boolean front2 = d2 > eps, back2 = d2 < -eps;
            boolean front3 = d3 > eps, back3 = d3 < -eps;
            boolean anyFront = front0 || front1 || front2 || front3;
            boolean anyBack = back0 || back1 || back2 || back3;
            if (anyFront && anyBack) {
                return Classification.SPANNING;
            }
            // The partition quad itself is coplanar. Keep it at the inner node;
            // classifying it as BACK would recurse with the same input forever.
            if (!anyFront && !anyBack) {
                return Classification.SPANNING;
            }
            if (anyFront) {
                return Classification.FRONT;
            }
            return Classification.BACK;
        }
    }
}
