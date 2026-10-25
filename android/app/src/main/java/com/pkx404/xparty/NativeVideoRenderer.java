package com.pkx404.xparty;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.view.Surface;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/** One decoder texture supplies the sharp player and the room's blurred light. */
public final class NativeVideoRenderer extends GLSurfaceView implements GLSurfaceView.Renderer, SurfaceTexture.OnFrameAvailableListener {
 public interface Listener { void surfaceReady(Surface surface); void frameReady(); void failed(String message); }
 private final Listener listener;
 private final FloatBuffer quad=ByteBuffer.allocateDirect(32).order(ByteOrder.nativeOrder()).asFloatBuffer();
 private SurfaceTexture texture;private Surface surface;private int external,videoProgram,blurProgram,bufferWidth=256,bufferHeight=144,width,height;
 private int[] textures=new int[2],fbos=new int[2];private final float[] transform=new float[16];
 private boolean pending=false,frame=false,notified=false,enabled=false;private float videoAspect=16f/9f;
 private float cssWidth=1,left,top,boxWidth=1,boxHeight=1;
 NativeVideoRenderer(Context context,Listener listener){super(context);this.listener=listener;quad.put(new float[]{-1,-1,1,-1,-1,1,1,1}).position(0);setEGLContextClientVersion(2);setPreserveEGLContextOnPause(true);setRenderer(this);setRenderMode(RENDERMODE_WHEN_DIRTY);}
 private static final String VERTEX="attribute vec2 position;varying vec2 uv;void main(){uv=position*.5+.5;gl_Position=vec4(position,0.,1.);}";
 private int program(String fragment){int vs=shader(GLES20.GL_VERTEX_SHADER,VERTEX),fs=shader(GLES20.GL_FRAGMENT_SHADER,fragment),p=GLES20.glCreateProgram();GLES20.glAttachShader(p,vs);GLES20.glAttachShader(p,fs);GLES20.glLinkProgram(p);int[] ok=new int[1];GLES20.glGetProgramiv(p,GLES20.GL_LINK_STATUS,ok,0);GLES20.glDeleteShader(vs);GLES20.glDeleteShader(fs);if(ok[0]==0)throw new IllegalStateException("Video shader link failed");return p;}
 private int shader(int kind,String source){int s=GLES20.glCreateShader(kind);GLES20.glShaderSource(s,source);GLES20.glCompileShader(s);int[] ok=new int[1];GLES20.glGetShaderiv(s,GLES20.GL_COMPILE_STATUS,ok,0);if(ok[0]==0)throw new IllegalStateException("Video shader compile failed");return s;}
 @Override public void onSurfaceCreated(GL10 ignored,EGLConfig config){
  try{
   frame=false;notified=false;pending=false;if(surface!=null)surface.release();if(texture!=null)texture.release();
   videoProgram=program("#extension GL_OES_EGL_image_external : require\nprecision mediump float;varying vec2 uv;uniform samplerExternalOES video;uniform mat4 transform;void main(){gl_FragColor=texture2D(video,(transform*vec4(uv,0.,1.)).xy);}");
   double[] kernel=new double[13];double sum=0;for(int i=0;i<13;i++){kernel[i]=Math.exp(-.5*i*i/64.);sum+=kernel[i]*(i==0?1:2);}StringBuilder samples=new StringBuilder("vec3 c=texture2D(image,uv).rgb*").append(number(kernel[0]/sum)).append(';');
   for(int i=1;i<13;i+=2){double w=kernel[i]+kernel[i+1],offset=(i*kernel[i]+(i+1)*kernel[i+1])/w;samples.append("c+=(texture2D(image,clamp(uv+direction*").append(number(offset)).append(",vec2(0.),vec2(1.))).rgb+texture2D(image,clamp(uv-direction*").append(number(offset)).append(",vec2(0.),vec2(1.))).rgb)*").append(number(w/sum)).append(';');}
   blurProgram=program("precision mediump float;varying vec2 uv;uniform sampler2D image;uniform vec2 direction;uniform float grade;void main(){"+samples+"if(grade>.5){float l=dot(c,vec3(.213,.715,.072));c=mix(vec3(l),c,1.5);c*=min(1.,.75/max(.0001,dot(c,vec3(.213,.715,.072))));c*=.72;}gl_FragColor=vec4(c,1.);}");
   int[] id=new int[1];GLES20.glGenTextures(1,id,0);external=id[0];GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,external);parameters(GLES11Ext.GL_TEXTURE_EXTERNAL_OES);
   texture=new SurfaceTexture(external);texture.setOnFrameAvailableListener(this);surface=new Surface(texture);
   GLES20.glGenTextures(2,textures,0);GLES20.glGenFramebuffers(2,fbos,0);
   for(int i=0;i<2;i++){GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,textures[i]);parameters(GLES20.GL_TEXTURE_2D);GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D,0,GLES20.GL_RGBA,bufferWidth,bufferHeight,0,GLES20.GL_RGBA,GLES20.GL_UNSIGNED_BYTE,null);GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER,fbos[i]);GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER,GLES20.GL_COLOR_ATTACHMENT0,GLES20.GL_TEXTURE_2D,textures[i],0);if(GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER)!=GLES20.GL_FRAMEBUFFER_COMPLETE)throw new IllegalStateException("Video framebuffer unavailable");}
   GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER,0);final Surface ready=surface;post(()->listener.surfaceReady(ready));
  }catch(Exception e){post(()->listener.failed("Native graphics unavailable"));}
 }
 private static String number(double v){return String.format(java.util.Locale.ROOT,"%.9f",v);}
 private void parameters(int target){GLES20.glTexParameteri(target,GLES20.GL_TEXTURE_MIN_FILTER,GLES20.GL_LINEAR);GLES20.glTexParameteri(target,GLES20.GL_TEXTURE_MAG_FILTER,GLES20.GL_LINEAR);GLES20.glTexParameteri(target,GLES20.GL_TEXTURE_WRAP_S,GLES20.GL_CLAMP_TO_EDGE);GLES20.glTexParameteri(target,GLES20.GL_TEXTURE_WRAP_T,GLES20.GL_CLAMP_TO_EDGE);}
 private void use(int p){GLES20.glUseProgram(p);quad.position(0);int location=GLES20.glGetAttribLocation(p,"position");GLES20.glEnableVertexAttribArray(location);GLES20.glVertexAttribPointer(location,2,GLES20.GL_FLOAT,false,0,quad);GLES20.glActiveTexture(GLES20.GL_TEXTURE0);}
 private void video(){use(videoProgram);GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,external);GLES20.glUniform1i(GLES20.glGetUniformLocation(videoProgram,"video"),0);GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(videoProgram,"transform"),1,false,transform,0);GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP,0,4);}
 private void blur(int input,float dx,float dy,float grade){use(blurProgram);GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,input);GLES20.glUniform1i(GLES20.glGetUniformLocation(blurProgram,"image"),0);GLES20.glUniform2f(GLES20.glGetUniformLocation(blurProgram,"direction"),dx,dy);GLES20.glUniform1f(GLES20.glGetUniformLocation(blurProgram,"grade"),grade);GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP,0,4);}
 @Override public void onSurfaceChanged(GL10 ignored,int w,int h){width=w;height=h;}
 @Override public void onDrawFrame(GL10 ignored){
  if(texture==null||videoProgram==0||width==0||height==0)return;
  synchronized(this){if(pending){texture.updateTexImage();texture.getTransformMatrix(transform);pending=false;frame=true;}}
  GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER,0);GLES20.glViewport(0,0,width,height);GLES20.glClearColor(.035f,.025f,.065f,1);GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
  if(!enabled||!frame)return;
  GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER,fbos[0]);GLES20.glViewport(0,0,bufferWidth,bufferHeight);video();
  GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER,fbos[1]);blur(textures[0],1f/bufferWidth,0,0);
  GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER,0);GLES20.glViewport(0,0,width,height);blur(textures[1],0,1f/bufferHeight,1);
  float scale=width/Math.max(1,cssWidth),w=boxWidth*scale,h=boxHeight*scale,x=left*scale,y=top*scale;
  if(w/h>videoAspect){float fitted=h*videoAspect;x+=(w-fitted)/2;w=fitted;}else{float fitted=w/videoAspect;y+=(h-fitted)/2;h=fitted;}
  if(w>0&&h>0){GLES20.glViewport(Math.round(x),Math.round(height-y-h),Math.round(w),Math.round(h));video();}
  if(!notified){notified=true;post(()->listener.frameReady());}
 }
 @Override public void onFrameAvailable(SurfaceTexture ignored){synchronized(this){pending=true;}requestRender();}
 void setVideoSize(int w,int h){if(w>0&&h>0)queueEvent(()->{videoAspect=(float)w/h;requestRender();});}
 void setBox(float viewport,float x,float y,float w,float h){queueEvent(()->{cssWidth=viewport;left=x;top=y;boxWidth=w;boxHeight=h;requestRender();});}
 void setVideoVisible(boolean value){queueEvent(()->{enabled=value;if(!value)notified=false;requestRender();});}
 void clearVideo(){queueEvent(()->{enabled=false;frame=false;pending=false;notified=false;requestRender();});}
 void release(){queueEvent(()->{enabled=false;if(surface!=null)surface.release();if(texture!=null)texture.release();texture=null;surface=null;GLES20.glDeleteTextures(2,textures,0);GLES20.glDeleteTextures(1,new int[]{external},0);GLES20.glDeleteFramebuffers(2,fbos,0);GLES20.glDeleteProgram(videoProgram);GLES20.glDeleteProgram(blurProgram);});}
}
