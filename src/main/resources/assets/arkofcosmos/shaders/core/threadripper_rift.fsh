#version 150

uniform sampler2D Sampler0;

in vec4 vertexColor;
in vec3 texCoord;
out vec4 fragColor;

float hash(vec2 p) {
    vec3 h = fract(vec3(p.xyx) * 0.1031);
    h += dot(h, h.yzx + 33.33);
    return fract((h.x + h.y) * h.z);
}

float noise(vec2 p) {
    vec2 i = floor(p), f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash(i), hash(i + vec2(1, 0)), f.x),
               mix(hash(i + vec2(0, 1)), hash(i + 1.0), f.x), f.y);
}

// Distance to the original sprite's opaque texels, not to its rectangular item quad.
float silhouetteDistance(vec2 uv) {
    ivec2 dims = textureSize(Sampler0, 0);
    vec2 pixel = uv * vec2(dims);
    ivec2 base = ivec2(floor(pixel));
    if (all(greaterThanEqual(base, ivec2(0))) && all(lessThan(base, dims))
            && texelFetch(Sampler0, base, 0).a > 0.1) return 0.0;
    float nearest = 9.0;
    vec2 local = fract(pixel) - 0.5;
    for (int y = -2; y <= 2; y++) for (int x = -2; x <= 2; x++) {
        ivec2 sampleAt = base + ivec2(x, y);
        if (any(lessThan(sampleAt, ivec2(0))) || any(greaterThanEqual(sampleAt, dims))) continue;
        if (texelFetch(Sampler0, sampleAt, 0).a < 0.1) continue;
        vec2 delta = max(abs(local - vec2(x, y)) - 0.5, 0.0);
        nearest = min(nearest, length(delta));
    }
    return nearest;
}

void main() {
    vec2 p = texCoord.xy * 2.0 - 1.0;
    float parameter = texCoord.z;
    vec3 tint = vertexColor.rgb;
    vec2 q = abs(p);
    // Every soft layer reaches zero before the quad edge.
    float window = (1.0 - smoothstep(0.68, 0.94, q.x)) * (1.0 - smoothstep(0.68, 0.94, q.y));
    if (parameter < -14.5) {
        // The UV axis runs from the arriving head back up the trail. Keep the head dense and the tail diffuse.
        float tail = texCoord.x;
        float bend = (0.018 * sin(tail * 16.0) + 0.028 * sin(tail * 31.0)) * sin(3.14159265 * tail);
        float edge = abs(p.y - bend);
        float head = smoothstep(0.005, 0.085, tail);
        float taper = 1.0 - smoothstep(0.58, 1.0, tail);
        float filament = exp(-edge * (18.0 + tail * 19.0));
        float flow = 0.67 + 0.33 * cos(tail * 31.0 - (-parameter - 15.0) * 16.0);
        float spread = exp(-edge * edge * 7.0);
        float sides = exp(-abs(edge - 0.30 - tail * 0.12) * 25.0) * flow;
        vec3 light = mix(tint, vec3(0.97, 0.99, 1.0), clamp(filament * 1.3, 0.0, 1.0));
        fragColor = vec4(light, vertexColor.a * head * taper * (1.0 - smoothstep(0.68, 0.94, q.y))
                             * clamp(filament + 0.43 * spread + 0.42 * sides, 0.0, 1.0));
        return;
    }
    if (parameter < -12.5) {
        float phase = -parameter - 13.0;
        vec2 uv = (vec2(texCoord.x, 1.0 - texCoord.y) - 0.5) * 1.25 + 0.5;
        float d = silhouetteDistance(uv);
        float width = tint.r;
        if (d <= 0.0 || d >= 1.68 * width) discard;
        float fromEdge = d / width;
        float line = exp(-fromEdge * 1.95);
        float outer = 1.0 - smoothstep(0.65, 1.68, fromEdge);
        float along = (uv.x + uv.y) * 0.5;
        float spectrum = 0.5 + 0.5 * sin(along * 21.0 - phase * 6.2831853 + d * 2.1);
        float sweep = pow(0.5 + 0.5 * cos(along * 6.2831853 - phase * 6.2831853), 9.0);
        vec3 hue = mix(vec3(0.58, 0.14, 1.0), vec3(0.12, 0.88, 1.0), spectrum);
        vec3 glow = mix(hue, vec3(0.81, 0.99, 1.0), clamp(line * (0.34 + 0.61 * sweep), 0.0, 1.0));
        fragColor = vec4(glow, vertexColor.a * outer * (0.29 + 0.71 * line));
        return;
    }
    if (parameter < -10.5) {
        float phase = clamp(-parameter - 11.0, 0.0, 1.0);
        float angle = atan(p.y, p.x);
        float r = length(p);
        float wave = 0.56 + phase * 0.24 + 0.033 * cos(6.0 * angle + phase * 4.0);
        float sectors = 0.23 + 0.77 * smoothstep(-0.15, 0.60, cos(angle * 3.0 - 2.1 + phase * 3.6));
        vec3 color = exp(-vec3(71.0) * abs(vec3(r - wave) - vec3(0.044, 0.012, -0.022)));
        vec3 aura = exp(-vec3(11.0) * abs(vec3(r - wave) - vec3(0.14, 0.04, -0.06)));
        vec3 prism = (color + aura * 0.27) * vec3(0.86, 0.70, 1.0);
        fragColor = vec4(prism, vertexColor.a * window * sectors);
        return;
    }
    if (parameter < -7.0) {
        if (parameter > -9.0) {
            // Blade: cyan inner light, blue/violet scatter and a moving hot filament.
            float beat = clamp(-parameter - 8.0, 0.0, 1.0);
            float wave = 0.72 + 0.28 * cos(p.x * 7.0 - beat * 6.2831853);
            float core = exp(-28.0 * q.y) * (0.67 + 0.33 * wave);
            float wash = exp(-q.y * q.y * 9.0) * (0.25 + wave * 0.16);
            vec3 glow = mix(tint, vec3(0.63, 0.86, 1.0), clamp(core, 0.0, 1.0));
            fragColor = vec4(glow, vertexColor.a * window * clamp(core * 0.65 + wash, 0.0, 1.0));
        } else {
            // Impact: a broad chromatic wash behind thin diffraction spikes and a hot nucleus.
            float r2 = dot(p, p);
            float core = exp(-24.0 * r2);
            float wash = exp(-4.3 * r2);
            vec2 spin = abs(mat2(0.92388, 0.38268, -0.38268, 0.92388) * p);
            float thin = exp(-46.0 * q.y - 1.9 * q.x) + exp(-43.0 * q.x - 2.5 * q.y);
            float prism = exp(-38.0 * spin.y - spin.x * 3.3) * 0.55;
            vec3 hue = mix(vec3(0.48, 0.12, 1.0), tint, smoothstep(0.0, 0.8, wash));
            vec3 glow = mix(hue, vec3(0.91, 0.99, 1.0), clamp(core + thin * 0.28, 0.0, 1.0));
            fragColor = vec4(glow, vertexColor.a * window * clamp(wash * 0.54 + core * 0.71 + thin * 0.35 + prism, 0.0, 1.0));
        }
        return;
    }
    if (parameter <= -5.5) {
        float edge = pow(max(0.0, 1.0 - p.x * p.x), 0.55);
        float core = (1.0 - smoothstep(0.018, 0.052 + fwidth(p.y) * 0.44, abs(p.y))) * edge;
        float corona = exp(-11.5 * abs(p.y)) * edge;
        fragColor = vec4(mix(tint, vec3(1.0), core), vertexColor.a * window * clamp(core + corona * 0.58, 0.0, 1.0));
        return;
    }
    if (parameter < -3.5) {
        // Sharp, broken shock front on top of the thin opening cut.
        float theta = atan(p.y, p.x);
        float arc = smoothstep(-0.14, 0.21, cos(theta * 5.0 + 0.6) + 0.38 * sin(theta * 11.0));
        float d = abs(length(p) - 0.78 - 0.028 * sin(theta * 8.0));
        float front = 1.0 - smoothstep(0.018, 0.041 + fwidth(d) * 0.35, d);
        float light = arc * (front * 0.79 + exp(-22.0 * d) * 0.20);
        fragColor = vec4(mix(tint, vec3(0.86, 0.97, 1.0), front * 0.67), vertexColor.a * window * light);
        return;
    }
    if (parameter < 0.0) {
        float shape = parameter < -2.5 ? exp(-4.0 * dot(p, p))
                     : (1.0 - smoothstep(0.15, 1.0, abs(p.x))) * (1.0 - smoothstep(0.35, 1.0, abs(p.y)));
        if (parameter > -1.5) shape = smoothstep(0.0, 0.55, texCoord.x) * (1.0 - smoothstep(0.91, 1.0, texCoord.x))
                                      * (0.55 + 0.45 * sin(texCoord.x * 8.0 - texCoord.y * 9.0));
        fragColor = vec4(tint, vertexColor.a * shape * (parameter < -1.5 ? window : 1.0));
        return;
    }
    float age = (mod(parameter, 2.0) - 0.25) * 2.0;
    float seed = floor(parameter * 0.5);
    float tip = max(0.0, 1.0 - p.x * p.x);
    float envelope = 1.0 - smoothstep(0.83, 1.0, abs(p.x));
    float open = smoothstep(0.012, 0.19, age) * (1.0 - smoothstep(0.48, 0.92, age));
    float fold = 0.06 * sin(p.x * 3.7 + seed) + 0.034 * sin(p.x * 12.0 - age * 11.0 + seed);
    float section = p.y - fold * tip * open;
    float gap = 0.50 * open * pow(tip, 0.74);
    float aa = clamp(fwidth(section) * 0.73, 0.005, 0.11);
    float seamA = abs(section - gap);
    float seamB = abs(section + gap * (0.77 + 0.16 * sin(p.x * 6.0 + seed)));
    float a = 1.0 - smoothstep(0.012, 0.027 + aa, seamA);
    float b = 1.0 - smoothstep(0.012, 0.027 + aa, seamB);
    float flare = (1.0 - smoothstep(0.015, 0.105, age)) * exp(-abs(section) * 9.5) * tip;
    float halo = exp(-13.0 * min(seamA, seamB)) * (0.18 + 0.15 * open);
    float inside = (1.0 - smoothstep(-aa, aa, max(section - gap, -section - gap * 0.83))) * open;
    vec2 cloudUv = vec2(p.x * 4.2 + seed * 0.72, section * 6.0);
    float cloudA = noise(cloudUv + vec2(-age * 4.6, 0)) * 0.72 + noise(cloudUv * 2.2 + vec2(age * 2.8, 12)) * 0.28;
    float cloudB = noise(cloudUv * 1.55 + vec2(age * 5.0, 3.8));
    vec2 stars = cloudUv * 4.1 + vec2(age * 9.0, 0);
    float starlight = step(0.89, hash(floor(stars))) * (1.0 - smoothstep(0.04, 0.30, length(fract(stars) - 0.5)));
    vec3 nebula = mix(vec3(0.11, 0.09, 0.55), vec3(0.14, 0.63, 0.98), smoothstep(0.38, 0.72, cloudA))
                * (0.20 + smoothstep(0.44, 0.67, cloudA) * 0.64)
                + vec3(0.70, 0.15, 0.85) * smoothstep(0.53, 0.76, cloudB) * 0.59;
    vec3 light = vec3(0.66, 0.91, 1.0) * a * 0.88 + vec3(0.76, 0.37, 1.0) * b * 0.76
               + vec3(0.62, 0.93, 1.0) * flare * 0.80 + mix(vec3(0.28, 0.49, 1.0), tint, 0.45) * halo
               + (nebula + vec3(starlight * 0.87)) * inside;
    fragColor = vec4(clamp(light * (0.55 + tint * 0.45), 0.0, 1.0), vertexColor.a * envelope * (1.0 - smoothstep(0.78, 1.0, q.y)));
}
