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
            for(String name:new String[]{"fx_sprite","fx_trail","galaxia_tint"}){
                int program=program(name.equals("galaxia_tint")?"fx_sprite":name,name);
                if(name.equals("galaxia_tint"))pixels(program);if(name.equals("fx_sprite"))zeroAlphaGlow(program);glDeleteProgram(program);
            }
            if(glGetError()!=GL_NO_ERROR)throw new AssertionError("OpenGL error");
            System.out.println("Galaxia GLSL compile/link and BasicTint framebuffer checks passed");
        }finally{GLFW.glfwDestroyWindow(window);GLFW.glfwTerminate();}
    }
    private static int compile(int type,String source){
        int shader=glCreateShader(type);glShaderSource(shader,source);glCompileShader(shader);
        if(glGetShaderi(shader,GL_COMPILE_STATUS)==GL_FALSE)throw new AssertionError(glGetShaderInfoLog(shader));return shader;
    }
    private static int program(String vertex,String fragment)throws Exception{
        int v=compile(GL_VERTEX_SHADER,Files.readString(ROOT.resolve(vertex+".vsh"))),f=compile(GL_FRAGMENT_SHADER,Files.readString(ROOT.resolve(fragment+".fsh")));
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
    private static int texture(float[] pixel){
        int id=glGenTextures();glBindTexture(GL_TEXTURE_2D,id);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MIN_FILTER,GL_NEAREST);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MAG_FILTER,GL_NEAREST);
        glTexImage2D(GL_TEXTURE_2D,0,GL_RGBA32F,1,1,0,GL_RGBA,GL_FLOAT,pixel);return id;
    }
    private static void near(float actual,float expected){if(Math.abs(actual-expected)>1e-5)throw new AssertionError(actual+" != "+expected);}
}
