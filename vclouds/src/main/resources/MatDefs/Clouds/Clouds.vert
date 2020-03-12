#import "MatDefs/Common/Compat.glsllib"

uniform float g_Time;
uniform float g_Aspect;

uniform mat4 g_ViewMatrix;
uniform vec3 g_CameraPosition;

in vec4 inColor;
in vec3 inPosition;
in vec2 inTexCoord;
in vec3 inNormal;

varying float time;
varying vec3 norm;
varying vec2 texCoord;

out vec3 pos;
out vec2 fpPos;

void main() {

    time = g_Time;
    texCoord = inTexCoord;
 
    vec3 v = vec3(inTexCoord.x, inTexCoord.y, -1.0)*mat3(g_ViewMatrix);

	pos = normalize(v.xyz);
 	fpPos = inPosition.xy*0.5+0.5;
 	
    gl_Position = vec4(inPosition.xy, 1.0, 1.0);
    
}