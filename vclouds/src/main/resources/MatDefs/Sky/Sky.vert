#import "Common/ShaderLib/Instancing.glsllib"

in vec3 inPosition;
in vec2 inTexCoord;
in vec3 inNormal;

out vec3 pos;
out vec4 shadowCoord;

void main(){
	vec3 v = vec3(inTexCoord.x, inTexCoord.y, -1.0)*mat3(g_ViewMatrix);
	pos = normalize(v.xyz);
    gl_Position = vec4(inPosition.xy, 1.0, 1.0);
}