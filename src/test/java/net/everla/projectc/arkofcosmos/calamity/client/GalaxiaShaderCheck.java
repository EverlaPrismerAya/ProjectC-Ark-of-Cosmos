package net.everla.projectc.arkofcosmos.calamity.client;

import java.nio.file.*;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL;
import static org.lwjgl.opengl.GL33.*;

/** Real driver compilation and floating-point framebuffer checks; opens only a hidden GLFW context. */
public final class GalaxiaShaderCheck {
    private static final Path ROOT=Path.of("src/main/resources/assets/arkofcosmos/shaders/core");
    public static void main(String[] args)throws Exception{
        if(!GLFW.glfwInit())throw new AssertionError("GLFW initialization failed");
        GLFW.glfwDefaultWindowHints();GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE,GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR,3);GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR,2);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE,GLFW.GLFW_OPENGL_CORE_PROFILE);
        long window=GLFW.glfwCreateWindow(16,16,"Galaxia shader regression",0,0);
        if(window==0)throw new AssertionError("No OpenGL 3.2 context");
        try{
            GLFW.glfwMakeContextCurrent(window);GL.createCapabilities();
            System.out.println("Shader driver: "+glGetString(GL_RENDERER)+" / "+glGetString(GL_VERSION));
            for(String name:new String[]{"fx_sprite","fx_trail","galaxia_tint","boss_bar","mahou_moon","mahou_forcefield","mahou_jujutsu"}){
                int program=program(name.equals("galaxia_tint")||name.equals("mahou_moon")||name.equals("mahou_forcefield")||name.equals("mahou_jujutsu")?"fx_sprite":name,name);
                if(name.equals("mahou_moon"))moonPixels(program);
                if(name.equals("mahou_jujutsu"))jujutsuPixels(program);
                if(name.equals("mahou_forcefield"))forcefieldPixels(program);
                if(name.equals("boss_bar"))bossBarPixels(program);
                if(name.equals("galaxia_tint"))pixels(program);if(name.equals("fx_sprite"))zeroAlphaGlow(program);glDeleteProgram(program);
            }
            try(var stream=GalaxiaShaderCheck.class.getResourceAsStream("/assets/minecraft/shaders/program/sobel.vsh")) {
                if(stream==null)throw new AssertionError("Missing Minecraft post-process vertex shader");
                String vertex=new String(stream.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
                for(String fragment:new String[]{"rewind","odamane"}) {
                    int program=programSources(vertex,Files.readString(ROOT.resolve("../program/"+fragment+".fsh")));
                    glDeleteProgram(program);
                }
                System.out.println("Odamane original post-process GLSL compile/link checks passed");
            }
            if(glGetError()!=GL_NO_ERROR)throw new AssertionError("OpenGL error");
            System.out.println("Galaxia GLSL compile/link and BasicTint framebuffer checks passed");
        }finally{GLFW.glfwDestroyWindow(window);GLFW.glfwTerminate();}
    }
    private static int compile(int type,String source){
        int shader=glCreateShader(type);glShaderSource(shader,source);glCompileShader(shader);
        if(glGetShaderi(shader,GL_COMPILE_STATUS)==GL_FALSE)throw new AssertionError(glGetShaderInfoLog(shader));return shader;
    }
    private static void jujutsuPixels(int program)throws Exception{
        glUseProgram(program);float[] identity={1,0,0,0,0,1,0,0,0,0,1,0,0,0,0,1};
        glUniformMatrix4fv(glGetUniformLocation(program,"ModelViewMat"),false,identity);glUniformMatrix4fv(glGetUniformLocation(program,"ProjMat"),false,identity);
        int vao=glGenVertexArrays();glBindVertexArray(vao);int vbo=glGenBuffers();glBindBuffer(GL_ARRAY_BUFFER,vbo);
        glBufferData(GL_ARRAY_BUFFER,new float[]{-1,-1,0,0,0,3,-1,0,2,0,-1,3,0,0,2},GL_STATIC_DRAW);
        int position=glGetAttribLocation(program,"Position"),uv=glGetAttribLocation(program,"UV0");
        glEnableVertexAttribArray(position);glVertexAttribPointer(position,3,GL_FLOAT,false,20,0);
        glEnableVertexAttribArray(uv);glVertexAttribPointer(uv,2,GL_FLOAT,false,20,12);glVertexAttrib4f(glGetAttribLocation(program,"Color"),1,1,1,1);
        int output=texture(null),fbo=glGenFramebuffers();glBindFramebuffer(GL_FRAMEBUFFER,fbo);glFramebufferTexture2D(GL_FRAMEBUFFER,GL_COLOR_ATTACHMENT0,GL_TEXTURE_2D,output,0);
        glBindTexture(GL_TEXTURE_2D,output);glTexImage2D(GL_TEXTURE_2D,0,GL_RGBA32F,128,128,0,GL_RGBA,GL_FLOAT,(java.nio.ByteBuffer)null);
        glViewport(0,0,128,128);glDisable(GL_BLEND);glDisable(GL_DEPTH_TEST);glDisable(GL_CULL_FACE);
        var preview=new java.awt.image.BufferedImage(640,128,java.awt.image.BufferedImage.TYPE_INT_RGB);
        for(int mode=0;mode<5;mode++){
            glUniform1f(glGetUniformLocation(program,"uMode"),mode);glUniform1f(glGetUniformLocation(program,"uTime"),1.5f);glDrawArrays(GL_TRIANGLES,0,3);
            float[] pixels=new float[128*128*4];glReadPixels(0,0,128,128,GL_RGBA,GL_FLOAT,pixels);
            int visible=0;
            for(int y=0;y<128;y++)for(int x=0;x<128;x++){
                int i=(y*128+x)*4,rgb=0;for(int c=0;c<4;c++)if(!Float.isFinite(pixels[i+c]))throw new AssertionError("Nonfinite jujutsu shader output");
                if(pixels[i+3]>.1)visible++;
                if((x==0||x==127)&&(y==0||y==127)&&pixels[i+3]>.001)throw new AssertionError("Opaque jujutsu corners");
                for(int c=0;c<3;c++)rgb=(rgb<<8)|Math.round(Math.min(1,Math.max(0,pixels[i+c]*pixels[i+3]))*255);
                preview.setRGB(mode*128+x,127-y,rgb);
            }
            if(visible<100)throw new AssertionError("Missing jujutsu mode "+mode);
        }
        var target=Path.of("build/reports/mahou-jujutsu.png");Files.createDirectories(target.getParent());javax.imageio.ImageIO.write(preview,"png",target.toFile());
        glDeleteFramebuffers(fbo);glDeleteTextures(output);glDeleteBuffers(vbo);glDeleteVertexArrays(vao);
        System.out.println("Jujutsu five modes: finite output, visible coverage and transparent corners passed");
    }
    private static void moonPixels(int program){
        glUseProgram(program);
        float[] identity={1,0,0,0,0,1,0,0,0,0,1,0,0,0,0,1};
        glUniformMatrix4fv(glGetUniformLocation(program,"ModelViewMat"),false,identity);
        glUniformMatrix4fv(glGetUniformLocation(program,"ProjMat"),false,identity);
        int vao=glGenVertexArrays();glBindVertexArray(vao);int buffer=glGenBuffers();glBindBuffer(GL_ARRAY_BUFFER,buffer);
        glBufferData(GL_ARRAY_BUFFER,new float[]{-1,-1,0,3,-1,0,-1,3,0},GL_STATIC_DRAW);
        int pos=glGetAttribLocation(program,"Position"),uv=glGetAttribLocation(program,"UV0"),color=glGetAttribLocation(program,"Color");
        glEnableVertexAttribArray(pos);glVertexAttribPointer(pos,3,GL_FLOAT,false,12,0);glVertexAttrib4f(color,1,1,1,1);
        int output=texture(null),fbo=glGenFramebuffers();glBindFramebuffer(GL_FRAMEBUFFER,fbo);glFramebufferTexture2D(GL_FRAMEBUFFER,GL_COLOR_ATTACHMENT0,GL_TEXTURE_2D,output,0);
        if(glCheckFramebufferStatus(GL_FRAMEBUFFER)!=GL_FRAMEBUFFER_COMPLETE)throw new AssertionError("Moon framebuffer incomplete");
        glViewport(0,0,1,1);glDisable(GL_BLEND);glDisable(GL_DEPTH_TEST);glDisable(GL_CULL_FACE);
        for(int mode=0;mode<2;mode++)for(float time:new float[]{0,1.5f,20}){
            glUniform1f(glGetUniformLocation(program,"uTime"),time);
            for(float[] sample:new float[][]{{0,0,0},{.895f,.5f,mode==0?1:0},{.25f,.5f,mode==1?1:0}}){
                glVertexAttrib3f(uv,sample[0],sample[1],mode);glDrawArrays(GL_TRIANGLES,0,3);
                float[] pixel=new float[4];glReadPixels(0,0,1,1,GL_RGBA,GL_FLOAT,pixel);
                for(float channel:pixel)if(!Float.isFinite(channel))throw new AssertionError("Nonfinite lunar output");
                if(sample[0]==0&&pixel[3]>.001)throw new AssertionError("Lunar quad has opaque corners");
                if(sample[2]==1&&pixel[3]<.5)throw new AssertionError("Missing moon/front pixels");
            }
        }
        glDeleteFramebuffers(fbo);glDeleteTextures(output);glDeleteBuffers(buffer);glDeleteVertexArrays(vao);
        System.out.println("Mahou lunar wave/crescent GLSL and transparent-corner pixel checks passed");
    }
    private static void forcefieldPixels(int program){
        glUseProgram(program);float[] identity={1,0,0,0,0,1,0,0,0,0,1,0,0,0,0,1};
        glUniformMatrix4fv(glGetUniformLocation(program,"ModelViewMat"),false,identity);glUniformMatrix4fv(glGetUniformLocation(program,"ProjMat"),false,identity);
        int vao=glGenVertexArrays();glBindVertexArray(vao);int vbo=glGenBuffers();glBindBuffer(GL_ARRAY_BUFFER,vbo);
        glBufferData(GL_ARRAY_BUFFER,new float[]{-1,-1,0,3,-1,0,-1,3,0},GL_STATIC_DRAW);
        int position=glGetAttribLocation(program,"Position"),uv=glGetAttribLocation(program,"UV0");
        glEnableVertexAttribArray(position);glVertexAttribPointer(position,3,GL_FLOAT,false,12,0);glVertexAttrib4f(glGetAttribLocation(program,"Color"),1,1,1,1);
        int input=texture(new float[]{1,1,1,1}),output=texture(null),fbo=glGenFramebuffers();
        glBindFramebuffer(GL_FRAMEBUFFER,fbo);glFramebufferTexture2D(GL_FRAMEBUFFER,GL_COLOR_ATTACHMENT0,GL_TEXTURE_2D,output,0);
        for(int unit=0;unit<2;unit++){glActiveTexture(GL_TEXTURE0+unit);glBindTexture(GL_TEXTURE_2D,input);glUniform1i(glGetUniformLocation(program,"Sampler"+unit),unit);}
        glViewport(0,0,1,1);glDisable(GL_BLEND);glDisable(GL_DEPTH_TEST);glDisable(GL_CULL_FACE);
        float[][] colors={{.35f,.65f,1,.45f,.75f,1},{.06f,.07f,.09f,.24f,.26f,.3f},{.05f,.85f,.8f,.4f,1,1}};
        for(int phase=0;phase<3;phase++){
            float[] c=colors[phase];glUniform3f(glGetUniformLocation(program,"uColor"),c[0],c[1],c[2]);glUniform3f(glGetUniformLocation(program,"uSecondaryColor"),c[3],c[4],c[5]);
            glUniform1f(glGetUniformLocation(program,"uTintStrength"),phase==1?.9f:.5f);glUniform1f(glGetUniformLocation(program,"uHealthRatio"),1);
            glUniform1f(glGetUniformLocation(program,"uTime"),0);glVertexAttrib3f(uv,.8f,.5f,1);glDrawArrays(GL_TRIANGLES,0,3);
            float[] pixel=new float[4];glReadPixels(0,0,1,1,GL_RGBA,GL_FLOAT,pixel);
            for(float channel:pixel)if(!Float.isFinite(channel))throw new AssertionError("Nonfinite forcefield output");
            if(pixel[3]<=0)throw new AssertionError("Missing forcefield ring");
            float healthyAlpha=pixel[3];
            if(healthyAlpha<=1f)throw new AssertionError("Forcefield intensity clipped before palette multiplication");
            // Isolate palette brightness to verify native color flicker independently of alpha.
            glUniform1f(glGetUniformLocation(program,"uTintStrength"),1);
            glDrawArrays(GL_TRIANGLES,0,3);glReadPixels(0,0,1,1,GL_RGBA,GL_FLOAT,pixel);
            float healthyBlue=pixel[2];
            float[][] cases={{.6f,0,1},{.3f,0,.9f},{.2f,0,.65f},{.05f,0,.65f},
                    {.04f,0,.56f*.75f},{.01f,0,.56f*.6f},{1,1,.56f}};
            for(float[] sample:cases){
                glUniform1f(glGetUniformLocation(program,"uHealthRatio"),sample[0]);
                glUniform1f(glGetUniformLocation(program,"uPostMusicHit"),sample[1]);
                glDrawArrays(GL_TRIANGLES,0,3);glReadPixels(0,0,1,1,GL_RGBA,GL_FLOAT,pixel);
                if(Math.abs(pixel[2]/healthyBlue-sample[2])>.01f)throw new AssertionError("Native palette flicker mismatch at health "+sample[0]);
                if(Math.abs(pixel[3]-healthyAlpha)>.001f)throw new AssertionError("Flicker incorrectly changes output alpha");
            }
            glUniform1f(glGetUniformLocation(program,"uPostMusicHit"),0);
            glUniform1f(glGetUniformLocation(program,"uTintStrength"),phase==1?.9f:.5f);
            glUniform1f(glGetUniformLocation(program,"uHealthRatio"),1);glDrawArrays(GL_TRIANGLES,0,3);glReadPixels(0,0,1,1,GL_RGBA,GL_FLOAT,pixel);
            if(phase==1&&Math.max(pixel[0],Math.max(pixel[1],pixel[2]))/pixel[3]>.4f)throw new AssertionError("Phase-two shield is not dark gray");
            if(phase!=1&&pixel[2]<=pixel[0])throw new AssertionError("Missing blue/cyan forcefield palette");
            glVertexAttrib3f(uv,0,0,1);glDrawArrays(GL_TRIANGLES,0,3);glReadPixels(0,0,1,1,GL_RGBA,GL_FLOAT,pixel);
            if(pixel[3]>.001)throw new AssertionError("Forcefield has opaque corners");
        }
        // Fusion's quad half-width is its gameplay radius: the shader boundary
        // must stay at UV radius .5 at every animation time.
        glUniform1f(glGetUniformLocation(program,"uFixedBoundary"),1);
        for(float time:new float[]{0,.5f,1,2}){
            glUniform1f(glGetUniformLocation(program,"uTime"),time);
            for(float offset:new float[]{.49f,.501f}){
                glVertexAttrib3f(uv,.5f+offset,.5f,1);glDrawArrays(GL_TRIANGLES,0,3);
                float[] pixel=new float[4];glReadPixels(0,0,1,1,GL_RGBA,GL_FLOAT,pixel);
                if(offset<.5f?pixel[3]<=0:pixel[3]>.001f)throw new AssertionError("Fusion shield visual boundary differs from damage radius");
            }
        }
        glUniform1f(glGetUniformLocation(program,"uFixedBoundary"),0);
        // Full-frame preview uses the shipped textures, not the white test texels.
        try {
            int[] images=new int[2];
            for(int unit=0;unit<2;unit++){
                var source=javax.imageio.ImageIO.read(ROOT.resolve("../../textures/fx/"+(unit==0?"supreme_forcefield":"supreme_noise")+".png").normalize().toFile());
                var bytes=org.lwjgl.BufferUtils.createByteBuffer(source.getWidth()*source.getHeight()*4);
                for(int y=0;y<source.getHeight();y++)for(int x=0;x<source.getWidth();x++){
                    int argb=source.getRGB(x,y);bytes.put((byte)(argb>>16)).put((byte)(argb>>8)).put((byte)argb).put((byte)(argb>>24));
                }
                bytes.flip();glActiveTexture(GL_TEXTURE0+unit);images[unit]=glGenTextures();glBindTexture(GL_TEXTURE_2D,images[unit]);
                glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MIN_FILTER,GL_LINEAR);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MAG_FILTER,GL_LINEAR);
                glTexImage2D(GL_TEXTURE_2D,0,GL_RGBA8,source.getWidth(),source.getHeight(),0,GL_RGBA,GL_UNSIGNED_BYTE,bytes);
            }
            glActiveTexture(GL_TEXTURE2);glBindTexture(GL_TEXTURE_2D,output);
            glTexImage2D(GL_TEXTURE_2D,0,GL_RGBA32F,512,512,0,GL_RGBA,GL_FLOAT,(java.nio.ByteBuffer)null);
            glBufferData(GL_ARRAY_BUFFER,new float[]{-1,-1,0,0,0, 3,-1,0,2,0, -1,3,0,0,2},GL_STATIC_DRAW);
            glVertexAttribPointer(position,3,GL_FLOAT,false,20,0);glEnableVertexAttribArray(uv);glVertexAttribPointer(uv,2,GL_FLOAT,false,20,12);
            glUniform3f(glGetUniformLocation(program,"uColor"),.35f,.65f,1);glUniform3f(glGetUniformLocation(program,"uSecondaryColor"),.45f,.75f,1);
            glUniform1f(glGetUniformLocation(program,"uHealthRatio"),1);glUniform1f(glGetUniformLocation(program,"uTintStrength"),.5f);
            glVertexAttrib4f(glGetAttribLocation(program,"Color"),1,1,1,.75f);
            glViewport(0,0,512,512);glDrawArrays(GL_TRIANGLES,0,3);
            float[] pixels=new float[512*512*4];glReadPixels(0,0,512,512,GL_RGBA,GL_FLOAT,pixels);
            var preview=new java.awt.image.BufferedImage(512,512,java.awt.image.BufferedImage.TYPE_INT_RGB);
            for(int y=0;y<512;y++)for(int x=0;x<512;x++){
                int i=(y*512+x)*4,rgb=0;
                for(int c=0;c<3;c++)rgb=(rgb<<8)|Math.round(Math.max(0,Math.min(1,pixels[i+c]))*255);
                preview.setRGB(x,511-y,rgb);
            }
            var target=Path.of("build/reports/mahou-forcefield.png");java.nio.file.Files.createDirectories(target.getParent());javax.imageio.ImageIO.write(preview,"png",target.toFile());
            for(int image:images)glDeleteTextures(image);
        } catch(java.io.IOException error){throw new AssertionError(error);}
        glDeleteFramebuffers(fbo);glDeleteTextures(input);glDeleteTextures(output);glDeleteBuffers(vbo);glDeleteVertexArrays(vao);glActiveTexture(GL_TEXTURE0);
        System.out.println("Supreme forcefield GLSL palette and transparent-corner checks passed");
    }
    private static int program(String vertex,String fragment)throws Exception{
        return programSources(Files.readString(ROOT.resolve(vertex+".vsh")),Files.readString(ROOT.resolve(fragment+".fsh")));
    }
    private static int programSources(String vertex,String fragment) {
        int v=compile(GL_VERTEX_SHADER,vertex),f=compile(GL_FRAGMENT_SHADER,fragment);
        int p=glCreateProgram();glAttachShader(p,v);glAttachShader(p,f);glLinkProgram(p);
        if(glGetProgrami(p,GL_LINK_STATUS)==GL_FALSE)throw new AssertionError(glGetProgramInfoLog(p));
        glDeleteShader(v);glDeleteShader(f);return p;
    }
    private static void pixels(int p){
        glUseProgram(p);
        float[] identity={1,0,0,0,0,1,0,0,0,0,1,0,0,0,0,1};
        glUniformMatrix4fv(glGetUniformLocation(p,"ModelViewMat"),false,identity);glUniformMatrix4fv(glGetUniformLocation(p,"ProjMat"),false,identity);
        glUniform3f(glGetUniformLocation(p,"FadeEye"),0,0,0);
        int vao=glGenVertexArrays();glBindVertexArray(vao);int buffer=glGenBuffers();glBindBuffer(GL_ARRAY_BUFFER,buffer);
        glBufferData(GL_ARRAY_BUFFER,new float[]{-1,-1,0, 3,-1,0, -1,3,0},GL_STATIC_DRAW);
        int position=glGetAttribLocation(p,"Position");glEnableVertexAttribArray(position);glVertexAttribPointer(position,3,GL_FLOAT,false,12,0);
        int uv=glGetAttribLocation(p,"UV0");glVertexAttrib3f(uv,.5f,.5f,1);
        // Nonwhite COLOR0 proves that BasicTint ignores the sprite's vertex color.
        int color=glGetAttribLocation(p,"Color");if(color>=0)glVertexAttrib4f(color,.1f,.2f,.3f,.4f);
        int input=texture(new float[]{.2f,.4f,.6f,.5f}),output=texture(null),fbo=glGenFramebuffers();
        glBindFramebuffer(GL_FRAMEBUFFER,fbo);glFramebufferTexture2D(GL_FRAMEBUFFER,GL_COLOR_ATTACHMENT0,GL_TEXTURE_2D,output,0);
        if(glCheckFramebufferStatus(GL_FRAMEBUFFER)!=GL_FRAMEBUFFER_COMPLETE)throw new AssertionError("Incomplete test framebuffer");
        glBindTexture(GL_TEXTURE_2D,input);glViewport(0,0,1,1);
        glUniform3f(glGetUniformLocation(p,"uColor"),.8f,.3f,.1f);
        for(float opacity:new float[]{0,.25f,1,1.25f}){
            glUniform1f(glGetUniformLocation(p,"uOpacity"),opacity);
            glDisable(GL_BLEND);glUniform1f(glGetUniformLocation(p,"NearFade"),0);glDrawArrays(GL_TRIANGLES,0,3);
            float[] pixel=new float[4];glReadPixels(0,0,1,1,GL_RGBA,GL_FLOAT,pixel);
            // HLSL rbg assignment: output green receives uColor.z, blue receives uColor.y.
            float[] expected={(.2f+(.8f-.2f)*opacity)*.5f,(.4f+(.1f-.4f)*opacity)*.5f,(.6f+(.3f-.6f)*opacity)*.5f,.25f};
            for(int i=0;i<4;i++)near(pixel[i],expected[i]);
            for(int source:new int[]{GL_ONE,GL_SRC_ALPHA})for(int destination:new int[]{GL_ONE,GL_ONE_MINUS_SRC_ALPHA}){
                glEnable(GL_BLEND);glBlendFunc(source,destination);glClearColor(.15f,.25f,.35f,.45f);glClear(GL_COLOR_BUFFER_BIT);glDrawArrays(GL_TRIANGLES,0,3);
                glReadPixels(0,0,1,1,GL_RGBA,GL_FLOAT,pixel);
                float[] backdrop={.15f,.25f,.35f,.45f};
                for(int i=0;i<4;i++)near(pixel[i],expected[i]*(source==GL_ONE?1:expected[3])+backdrop[i]*(destination==GL_ONE?1:1-expected[3]));
            }
        }
        glDisable(GL_BLEND);glUniform1f(glGetUniformLocation(p,"uOpacity"),0);
        glUniform1f(glGetUniformLocation(p,"NearFade"),1);
        for(int premultiplied=0;premultiplied<2;premultiplied++){
            glUniform1f(glGetUniformLocation(p,"FadePremultiplied"),premultiplied);glDrawArrays(GL_TRIANGLES,0,3);
            float[] pixel=new float[4];glReadPixels(0,0,1,1,GL_RGBA,GL_FLOAT,pixel);
            near(pixel[0],.1f*(premultiplied==1?.12f:1));near(pixel[3],.25f*.12f);
        }
        glDeleteFramebuffers(fbo);glDeleteTextures(input);glDeleteTextures(output);glDeleteBuffers(buffer);glDeleteVertexArrays(vao);
    }
    private static void zeroAlphaGlow(int p){
        glUseProgram(p);float[] identity={1,0,0,0,0,1,0,0,0,0,1,0,0,0,0,1};
        glUniformMatrix4fv(glGetUniformLocation(p,"ModelViewMat"),false,identity);glUniformMatrix4fv(glGetUniformLocation(p,"ProjMat"),false,identity);
        int vao=glGenVertexArrays();glBindVertexArray(vao);int vbo=glGenBuffers();glBindBuffer(GL_ARRAY_BUFFER,vbo);glBufferData(GL_ARRAY_BUFFER,new float[]{-1,-1,0,3,-1,0,-1,3,0},GL_STATIC_DRAW);
        int position=glGetAttribLocation(p,"Position");glEnableVertexAttribArray(position);glVertexAttribPointer(position,3,GL_FLOAT,false,12,0);glVertexAttrib3f(glGetAttribLocation(p,"UV0"),.5f,.5f,1);glVertexAttrib4f(glGetAttribLocation(p,"Color"),.5f,.25f,.75f,0);
        int input=texture(new float[]{.2f,.4f,.6f,.5f}),output=texture(null),fbo=glGenFramebuffers();glBindFramebuffer(GL_FRAMEBUFFER,fbo);glFramebufferTexture2D(GL_FRAMEBUFFER,GL_COLOR_ATTACHMENT0,GL_TEXTURE_2D,output,0);glBindTexture(GL_TEXTURE_2D,input);glViewport(0,0,1,1);
        glEnable(GL_BLEND);glBlendFunc(GL_ONE,GL_ONE_MINUS_SRC_ALPHA);glClearColor(.1f,.2f,.3f,.4f);glClear(GL_COLOR_BUFFER_BIT);glDrawArrays(GL_TRIANGLES,0,3);
        float[] pixel=new float[4];glReadPixels(0,0,1,1,GL_RGBA,GL_FLOAT,pixel);near(pixel[0],.2f);near(pixel[1],.3f);near(pixel[2],.75f);near(pixel[3],.4f);
        glDisable(GL_BLEND);glDeleteFramebuffers(fbo);glDeleteTextures(input);glDeleteTextures(output);glDeleteBuffers(vbo);glDeleteVertexArrays(vao);
    }
    private static void bossBarPixels(int p)throws Exception {
        glUseProgram(p);
        float[] identity={1,0,0,0,0,1,0,0,0,0,1,0,0,0,0,1};
        glUniformMatrix4fv(glGetUniformLocation(p,"ModelViewMat"),false,identity);
        glUniformMatrix4fv(glGetUniformLocation(p,"ProjMat"),false,identity);
        int vao=glGenVertexArrays();glBindVertexArray(vao);
        int vbo=glGenBuffers();glBindBuffer(GL_ARRAY_BUFFER,vbo);
        // Upload the exact production quad, not a synthetic fullscreen triangle.
        var builder=new com.mojang.blaze3d.vertex.BufferBuilder(256);
        var emit=net.everla.projectc.arkofcosmos.client.MahouBossBar.class.getDeclaredMethod("writeQuad",
                com.mojang.blaze3d.vertex.BufferBuilder.class,org.joml.Matrix4f.class,int.class,int.class,int.class,int.class,float.class);
        emit.setAccessible(true);
        emit.invoke(null,builder,new org.joml.Matrix4f(),0,0,201,48,1f);
        var mesh=builder.end();glBufferData(GL_ARRAY_BUFFER,mesh.vertexBuffer(),GL_STATIC_DRAW);mesh.release();
        int position=glGetAttribLocation(p,"Position");glEnableVertexAttribArray(position);
        glVertexAttribPointer(position,3,GL_FLOAT,false,24,0);
        int uv=glGetAttribLocation(p,"UV0");glEnableVertexAttribArray(uv);glVertexAttribPointer(uv,2,GL_FLOAT,false,24,12);
        int colorAttribute=glGetAttribLocation(p,"Color");glEnableVertexAttribArray(colorAttribute);glVertexAttribPointer(colorAttribute,4,GL_UNSIGNED_BYTE,true,24,20);
        int indices=glGenBuffers();glBindBuffer(GL_ELEMENT_ARRAY_BUFFER,indices);
        glBufferData(GL_ELEMENT_ARRAY_BUFFER,new int[]{0,1,2,2,3,0},GL_STATIC_DRAW);
        float[] projection=new float[16];new org.joml.Matrix4f().setOrtho(0,201,16,0,-1,1).get(projection);
        glUniformMatrix4fv(glGetUniformLocation(p,"ProjMat"),false,projection);
        var image=javax.imageio.ImageIO.read(ROOT.resolve("../../textures/gui/boss_bar.png").normalize().toFile());
        var rgba=java.nio.ByteBuffer.allocateDirect(image.getWidth()*image.getHeight()*4);
        for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++){
            int color=image.getRGB(x,y);
            rgba.put((byte)(color>>16)).put((byte)(color>>8)).put((byte)color).put((byte)(color>>24));
        }
        rgba.flip();
        int atlas=glGenTextures();glBindTexture(GL_TEXTURE_2D,atlas);
        glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MIN_FILTER,GL_NEAREST);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MAG_FILTER,GL_NEAREST);
        glTexImage2D(GL_TEXTURE_2D,0,GL_RGBA8,image.getWidth(),image.getHeight(),0,GL_RGBA,GL_UNSIGNED_BYTE,rgba);
        int stars=texture(new float[]{1,1,1,1}),output=texture(null),fbo=glGenFramebuffers();
        glBindFramebuffer(GL_FRAMEBUFFER,fbo);glFramebufferTexture2D(GL_FRAMEBUFFER,GL_COLOR_ATTACHMENT0,GL_TEXTURE_2D,output,0);
        glActiveTexture(GL_TEXTURE0);glBindTexture(GL_TEXTURE_2D,atlas);glUniform1i(glGetUniformLocation(p,"Sampler0"),0);
        for(int i=0;i<10;i++){
            glActiveTexture(GL_TEXTURE1+i);glBindTexture(GL_TEXTURE_2D,stars);glUniform1i(glGetUniformLocation(p,"Cosmic"+i),i+1);
        }
        glDisable(GL_DEPTH_TEST);glDepthMask(false);glDisable(GL_BLEND);glViewport(0,0,1,1);
        // Minecraft GUI projection reverses Y, and normal HUD rendering enables back-face culling.
        glEnable(GL_CULL_FACE);glCullFace(GL_BACK);glFrontFace(GL_CCW);
        glClearColor(0,0,0,0);glClear(GL_COLOR_BUFFER_BIT);glDrawElements(GL_TRIANGLES,6,GL_UNSIGNED_INT,0);
        float[] pixel=new float[4];glReadPixels(0,0,1,1,GL_RGBA,GL_FLOAT,pixel);
        near(pixel[3],1);
        if(!(pixel[0]+pixel[1]+pixel[2]>.1f))throw new AssertionError("Production GUI boss quad is invisible with culling enabled");
        // Exercise production UV helpers through the real vertex shader at GUI pixel scale.
        String source=Files.readString(ROOT.resolve("boss_bar.fsh")).replace("void main()", "void originalMain()");
        int mapping=programSources(Files.readString(ROOT.resolve("boss_bar.vsh")), source+
                "\nvoid main(){vec2 uv=layerUv(fPos.xy,uTime);fragColor=vec4(uv,0,1);}");
        glUseProgram(mapping);
        glUniformMatrix4fv(glGetUniformLocation(mapping,"ModelViewMat"),false,identity);
        glUniformMatrix4fv(glGetUniformLocation(mapping,"ProjMat"),false,projection);
        // Rebind attributes: locations are assigned independently by each linked program.
        int mapPosition=glGetAttribLocation(mapping,"Position"), mapUv=glGetAttribLocation(mapping,"UV0");
        glEnableVertexAttribArray(mapPosition);glVertexAttribPointer(mapPosition,3,GL_FLOAT,false,24,0);
        glEnableVertexAttribArray(mapUv);glVertexAttribPointer(mapUv,2,GL_FLOAT,false,24,12);
        glActiveTexture(GL_TEXTURE0);glBindTexture(GL_TEXTURE_2D,output);
        glTexImage2D(GL_TEXTURE_2D,0,GL_RGBA32F,201,16,0,GL_RGBA,GL_FLOAT,(java.nio.ByteBuffer)null);
        glViewport(0,0,201,16);
        for(float angle:new float[]{0,.3f,.7853982f,1.5707963f,3f,20f}) {
            glUniform1f(glGetUniformLocation(mapping,"uTime"),angle);
            glDrawElements(GL_TRIANGLES,6,GL_UNSIGNED_INT,0);
            float[] center=new float[4],right=new float[4],up=new float[4];
            glReadPixels(100,8,1,1,GL_RGBA,GL_FLOAT,center);
            glReadPixels(101,8,1,1,GL_RGBA,GL_FLOAT,right);
            glReadPixels(100,9,1,1,GL_RGBA,GL_FLOAT,up);
            float dx=(float)Math.hypot(right[0]-center[0],right[1]-center[1]);
            float dy=(float)Math.hypot(up[0]-center[0],up[1]-center[1]);
            near(dx,16f/50f);near(dy,16f/50f);
        }
        glDeleteProgram(mapping);
        int sampling=programSources(Files.readString(ROOT.resolve("boss_bar.vsh")),source+
                "\nvoid main(){fragColor=frame(Cosmic0,vec2(.5),Frame0);}");
        glUseProgram(sampling);
        glUniformMatrix4fv(glGetUniformLocation(sampling,"ModelViewMat"),false,identity);
        glUniformMatrix4fv(glGetUniformLocation(sampling,"ProjMat"),false,projection);
        int samplePosition=glGetAttribLocation(sampling,"Position");
        glEnableVertexAttribArray(samplePosition);glVertexAttribPointer(samplePosition,3,GL_FLOAT,false,24,0);
        var strip=java.nio.ByteBuffer.allocateDirect(16*64*4);
        for(int y=0;y<64;y++)for(int x=0;x<16;x++)strip.put((byte)(y/16*60)).put((byte)0).put((byte)0).put((byte)255);
        strip.flip();glBindTexture(GL_TEXTURE_2D,stars);
        glTexImage2D(GL_TEXTURE_2D,0,GL_RGBA8,16,64,0,GL_RGBA,GL_UNSIGNED_BYTE,strip);
        glUniform1i(glGetUniformLocation(sampling,"Cosmic0"),0);
        for(int frame=0;frame<4;frame++) {
            glUniform1f(glGetUniformLocation(sampling,"Frame0"),frame);
            glDrawElements(GL_TRIANGLES,6,GL_UNSIGNED_INT,0);
            glReadPixels(100,8,1,1,GL_RGBA,GL_FLOAT,pixel);
            near(pixel[0],frame*60f/255f);
        }
        glDeleteProgram(sampling);
        glUseProgram(p);
        glEnableVertexAttribArray(position);glVertexAttribPointer(position,3,GL_FLOAT,false,24,0);
        glEnableVertexAttribArray(uv);glVertexAttribPointer(uv,2,GL_FLOAT,false,24,12);
        glEnableVertexAttribArray(colorAttribute);glVertexAttribPointer(colorAttribute,4,GL_UNSIGNED_BYTE,true,24,20);
        int[] starTextures=new int[10];
        for(int i=0;i<10;i++) {
            var icon=javax.imageio.ImageIO.read(ROOT.resolve("../../textures/misc/cosmic/cosmic_"+i+".png").normalize().toFile());
            var data=java.nio.ByteBuffer.allocateDirect(icon.getWidth()*icon.getHeight()*4);
            for(int y=0;y<icon.getHeight();y++)for(int x=0;x<icon.getWidth();x++){
                int c=icon.getRGB(x,y);data.put((byte)(c>>16)).put((byte)(c>>8)).put((byte)c).put((byte)(c>>24));
            }
            data.flip();starTextures[i]=glGenTextures();glActiveTexture(GL_TEXTURE1+i);glBindTexture(GL_TEXTURE_2D,starTextures[i]);
            glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MIN_FILTER,GL_NEAREST);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MAG_FILTER,GL_NEAREST);
            glTexImage2D(GL_TEXTURE_2D,0,GL_RGBA8,icon.getWidth(),icon.getHeight(),0,GL_RGBA,GL_UNSIGNED_BYTE,data);
        }
        glActiveTexture(GL_TEXTURE0);glBindTexture(GL_TEXTURE_2D,output);
        glTexImage2D(GL_TEXTURE_2D,0,GL_RGBA32F,804,64,0,GL_RGBA,GL_FLOAT,(java.nio.ByteBuffer)null);
        glBindTexture(GL_TEXTURE_2D,atlas);glViewport(0,0,804,64);
        var preview=new java.awt.image.BufferedImage(804,64*4,java.awt.image.BufferedImage.TYPE_INT_ARGB);
        for(int row=0;row<4;row++){
            glUniform1f(glGetUniformLocation(p,"uTime"),row*40f);
            glDrawElements(GL_TRIANGLES,6,GL_UNSIGNED_INT,0);
            var data=java.nio.ByteBuffer.allocateDirect(804*64*4);glReadPixels(0,0,804,64,GL_RGBA,GL_UNSIGNED_BYTE,data);
            for(int y=0;y<64;y++)for(int x=0;x<804;x++){
                int offset=(y*804+x)*4;
                int c=(data.get(offset+3)&255)<<24|(data.get(offset)&255)<<16|(data.get(offset+1)&255)<<8|(data.get(offset+2)&255);
                preview.setRGB(x,row*64+63-y,c);
            }
        }
        javax.imageio.ImageIO.write(preview,"png",Path.of("build/boss-cosmos-preview.png").toFile());
        for(int texture:starTextures)glDeleteTextures(texture);
        System.out.println("Boss cosmos equal XY scale at six rotations and square animation-frame sampling passed");
        glDisable(GL_CULL_FACE);glDeleteBuffers(indices);
        glDepthMask(true);glActiveTexture(GL_TEXTURE0);
        glDeleteFramebuffers(fbo);glDeleteTextures(atlas);glDeleteTextures(stars);glDeleteTextures(output);glDeleteBuffers(vbo);glDeleteVertexArrays(vao);
        System.out.println("Boss bar production-quad GUI projection/culling pixel check passed");
    }
    private static int texture(float[] pixel){
        int id=glGenTextures();glBindTexture(GL_TEXTURE_2D,id);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MIN_FILTER,GL_NEAREST);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MAG_FILTER,GL_NEAREST);
        glTexImage2D(GL_TEXTURE_2D,0,GL_RGBA32F,1,1,0,GL_RGBA,GL_FLOAT,pixel);return id;
    }
    private static void near(float actual,float expected){if(Math.abs(actual-expected)>1e-5)throw new AssertionError(actual+" != "+expected);}
}
