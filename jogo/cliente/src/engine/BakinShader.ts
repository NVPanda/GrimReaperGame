// src/engine/BakinShader.ts
export const BAKIN_VERTEX_SHADER = `#version 300 es
precision highp float;

layout(location = 0) in vec2 a_position;
layout(location = 1) in vec2 a_texCoord;

uniform vec2 u_resolution;
uniform vec3 u_worldPos;   // x, y, z (profundidade 2.5D)
uniform vec2 u_spriteSize; // ex: 80x80 (Vibrato) ou 40x40 (Standard)
uniform vec4 u_uvRect;     // x, y, width, height do frame atual na folha 3x4

out vec2 v_texCoord;
out vec3 v_worldPos;

void main() {
    vec2 scaledPos = a_position * u_spriteSize;
    vec2 screenPos = u_worldPos.xy + scaledPos;

    // Converte pixels para clip space (-1 a +1)
    vec2 zeroToOne = screenPos / u_resolution;
    vec2 clipSpace = (zeroToOne * 2.0 - 1.0) * vec2(1.0, -1.0);

    gl_Position = vec4(clipSpace, u_worldPos.z, 1.0);
    v_texCoord = u_uvRect.xy + (a_texCoord * u_uvRect.zw);
    v_worldPos = vec3(screenPos, u_worldPos.z);
}
`;

export const BAKIN_FRAGMENT_SHADER = `#version 300 es
precision highp float;

in vec2 v_texCoord;
in vec3 v_worldPos;

uniform sampler2D u_diffuseMap; // Sprite .png gerado pelo Bakin Studio
uniform sampler2D u_normalMap;  // Normal Map _n.png gerado pelo Bakin Studio

uniform vec3 u_ambientColor;    // Cor ambiente noturna/caverna/vila
uniform vec3 u_lightPos;        // Posição (x, y, z) do poste de luz ou cristal
uniform vec3 u_lightColor;      // Cor da luz (ex: âmbar quente ou ciano neon)
uniform float u_lightRadius;    // Raio de alcance da luz

out vec4 outColor;

void main() {
    vec4 baseColor = texture(u_diffuseMap, v_texCoord);
    if (baseColor.a < 0.1) {
        discard; // Recorte exato de Pixel Art (sem bordas borradas)
    }

    // Extrai o vetor normal do arquivo _n.png (-1.0 a 1.0)
    vec3 normal = normalize(texture(u_normalMap, v_texCoord).rgb * 2.0 - 1.0);

    // Cálculo de luz pontual 2.5D estilo RPG Developer Bakin
    vec3 lightDelta = u_lightPos - v_worldPos;
    float dist = length(lightDelta);
    vec3 lightDir = normalize(lightDelta);

    float attenuation = clamp(1.0 - (dist / u_lightRadius), 0.0, 1.0);
    attenuation *= attenuation; // Decaimento suave quadrático

    // Produto escalar entre a normal do pixel e a direção da luz 3D
    float diff = max(dot(normal, lightDir), 0.0);
    vec3 pointLight = u_lightColor * diff * attenuation * 1.6;

    vec3 finalRgb = baseColor.rgb * (u_ambientColor + pointLight);
    outColor = vec4(finalRgb, baseColor.a);
}
`;

/**
 * Carrega a textura no WebGL2 travando gl.NEAREST para manter os pixels 40x40 e 80x80 perfeitos
 */
export function loadPixelArtTexture(gl: WebGL2RenderingContext, url: string): Promise<WebGLTexture> {
  return new Promise((resolve, reject) => {
    const tex = gl.createTexture();
    if (!tex) return reject("Falha ao criar WebGLTexture");

    const img = new Image();
    img.onload = () => {
      gl.bindTexture(gl.TEXTURE_2D, tex);
      gl.texImage2D(gl.TEXTURE_2D, 0, gl.RGBA, gl.RGBA, gl.UNSIGNED_BYTE, img);
      // CRÍTICO PARA PIXEL ART: Proíbe interpolação linear
      gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MIN_FILTER, gl.NEAREST);
      gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MAG_FILTER, gl.NEAREST);
      gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_S, gl.CLAMP_TO_EDGE);
      gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_T, gl.CLAMP_TO_EDGE);
      resolve(tex);
    };
    img.onerror = reject;
    img.src = url;
  });
}