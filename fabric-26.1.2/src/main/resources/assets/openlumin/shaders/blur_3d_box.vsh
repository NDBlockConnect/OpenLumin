#version 410 core

#moj_import <minecraft:dynamictransforms.glsl>

// MC 1.21.10+ uses UBO for projection matrix
layout(std140) uniform Projection {
    mat4 ProjMat;
};

in vec3 Position;
in vec4 Color;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
}
