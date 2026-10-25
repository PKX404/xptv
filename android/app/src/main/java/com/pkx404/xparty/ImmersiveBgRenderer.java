package com.pkx404.xparty;

import android.content.Context;
import android.graphics.*;
import android.media.MediaPlayer;
import android.os.*;
import android.util.AttributeSet;
import android.view.*;
import android.widget.FrameLayout;

/** A normal foreground player plus a 20x12, 10 Hz ambient layer. */
public final class ImmersiveBgRenderer extends FrameLayout implements AutoCloseable {
 public static final int SAMPLE_WIDTH=20,SAMPLE_HEIGHT=12,INTERVAL_MS=100;
 public static final float BLUR_PX=25f,SATURATION=1.3f,BRIGHTNESS=.8f,BACK_SCALE=1.3f;
 public interface Result { void complete(boolean success); }
 public interface FrameSource { void copy(Bitmap destination,Result result); }
 // ExoPlayer exposes setVideoSurface/clearVideoSurface, not getVideoFrame().
 public interface SurfaceBinding { void attach(Surface surface);void detach(Surface surface); }
 public interface FailureListener { void unavailable(); }

 private final Handler handler=new Handler(Looper.getMainLooper());
 private final AmbientView back;
 private final TextureView front;
 private final Bitmap capture=Bitmap.createBitmap(SAMPLE_WIDTH,SAMPLE_HEIGHT,Bitmap.Config.ARGB_8888);
 private final Bitmap display=Bitmap.createBitmap(SAMPLE_WIDTH,SAMPLE_HEIGHT,Bitmap.Config.ARGB_8888);
 private final int[] pixels=new int[SAMPLE_WIDTH*SAMPLE_HEIGHT],scratch=new int[pixels.length];
 private FrameSource source;private SurfaceBinding binding;private Surface surface;
 private FailureListener failureListener;private boolean running,inFlight,closed,hasFrame;
 private long generation,lastSubmission;private int failures,videoWidth,videoHeight;
 private final Paint copyPaint=new Paint(Paint.FILTER_BITMAP_FLAG);

 public ImmersiveBgRenderer(Context context){this(context,null);}
 public ImmersiveBgRenderer(Context context,AttributeSet attrs){super(context,attrs);
  setClipChildren(true);setClipToPadding(true);setBackgroundColor(Color.rgb(30,21,40));
  back=new AmbientView(context);addView(back,new LayoutParams(-1,-1));back.setScaleX(BACK_SCALE);back.setScaleY(BACK_SCALE);
  if(Build.VERSION.SDK_INT>=31)back.setRenderEffect(RenderEffect.createBlurEffect(BLUR_PX,BLUR_PX,Shader.TileMode.CLAMP));
  front=new TextureView(context);front.setOpaque(false);addView(front,new LayoutParams(-1,-1,Gravity.CENTER));
  front.setSurfaceTextureListener(new TextureView.SurfaceTextureListener(){
   public void onSurfaceTextureAvailable(SurfaceTexture texture,int w,int h){if(closed)return;surface=new Surface(texture);if(binding!=null)binding.attach(surface);refresh();}
   public void onSurfaceTextureSizeChanged(SurfaceTexture texture,int w,int h){}
   public boolean onSurfaceTextureDestroyed(SurfaceTexture texture){unbindSurface();return true;}
   public void onSurfaceTextureUpdated(SurfaceTexture texture){}
  });
 }
 public TextureView foreground(){return front;}
 public void setFailureListener(FailureListener listener){failureListener=listener;}
 public void setVideoSize(int width,int height){checkMain();videoWidth=width;videoHeight=height;fitVideo();}
 private void fitVideo(){if(videoWidth<=0||videoHeight<=0||getWidth()==0||getHeight()==0)return;float scale=Math.min((float)getWidth()/videoWidth,(float)getHeight()/videoHeight);LayoutParams p=(LayoutParams)front.getLayoutParams();int w=Math.max(1,Math.round(videoWidth*scale)),h=Math.max(1,Math.round(videoHeight*scale));if(p.width!=w||p.height!=h){p.width=w;p.height=h;p.gravity=Gravity.CENTER;front.setLayoutParams(p);}}
 @Override protected void onSizeChanged(int w,int h,int oldW,int oldH){super.onSizeChanged(w,h,oldW,oldH);fitVideo();}

 /** The caller retains player ownership. HLS/IPTV uses the same decoder surface. */
 public void connect(SurfaceBinding next){checkMain();if(closed)throw new IllegalStateException("Renderer is closed");if(binding!=null&&surface!=null)binding.detach(surface);binding=next;for(int i=getChildCount()-1;i>=2;i--)removeViewAt(i);front.setVisibility(VISIBLE);if(next!=null&&surface!=null)next.attach(surface);setFrameSource(textureFrames(front));}
 public void connect(MediaPlayer player){connect(new SurfaceBinding(){public void attach(Surface s){player.setSurface(s);}public void detach(Surface s){player.setSurface(null);}});}
 /** Keep an external PlayerView/WebView as the sharp foreground; never blur it. */
 public void setExternalForeground(View view,FrameSource frames){checkMain();if(closed)throw new IllegalStateException("Renderer is closed");if(view==front||view==back)throw new IllegalArgumentException("Use an external player view");if(view.getParent()!=null&&view.getParent()!=this)throw new IllegalArgumentException("Detach foreground from its previous parent first");if(binding!=null&&surface!=null)binding.detach(surface);binding=null;front.setVisibility(GONE);for(int i=getChildCount()-1;i>=2;i--)removeViewAt(i);if(view.getParent()!=this)addView(view,new LayoutParams(-1,-1));setFrameSource(frames);}
 public void setFrameSource(FrameSource next){checkMain();if(closed)throw new IllegalStateException("Renderer is closed");generation++;source=next;failures=0;hasFrame=false;back.invalidate();refresh();}
 public void submitFrame(Bitmap bitmap){checkMain();long now=SystemClock.uptimeMillis();if(closed||inFlight||now-lastSubmission<INTERVAL_MS||bitmap==null||bitmap.isRecycled())return;lastSubmission=now;new Canvas(capture).drawBitmap(bitmap,null,new Rect(0,0,SAMPLE_WIDTH,SAMPLE_HEIGHT),copyPaint);publish();}

 public static FrameSource textureFrames(TextureView view){return (target,result)->{if(!view.isAvailable()){result.complete(false);return;}try{result.complete(view.getBitmap(target)!=null);}catch(RuntimeException error){result.complete(false);}};}
 public static FrameSource surfaceFrames(SurfaceView view){return (target,result)->{try{if(!view.getHolder().getSurface().isValid()){result.complete(false);return;}PixelCopy.request(view,target,status->result.complete(status==PixelCopy.SUCCESS),new Handler(Looper.getMainLooper()));}catch(IllegalArgumentException error){result.complete(false);}};}
 /** Visible, unprotected WebView region only; WebView exposes no MediaPlayer. */
 public static FrameSource webViewFrames(Window window,View videoRegion){return (target,result)->{
  if(!videoRegion.isShown()||videoRegion.getWidth()==0||videoRegion.getHeight()==0){result.complete(false);return;}
  int[] at=new int[2];videoRegion.getLocationInWindow(at);Rect rect=new Rect(at[0],at[1],at[0]+videoRegion.getWidth(),at[1]+videoRegion.getHeight());
  if(!rect.intersect(0,0,window.getDecorView().getWidth(),window.getDecorView().getHeight())){result.complete(false);return;}
  try{PixelCopy.request(window,rect,target,status->result.complete(status==PixelCopy.SUCCESS),new Handler(Looper.getMainLooper()));}catch(IllegalArgumentException error){result.complete(false);}
 };}

 private final Runnable tick=new Runnable(){public void run(){
  if(!running||source==null||inFlight)return;inFlight=true;final long revision=generation,started=SystemClock.uptimeMillis();final boolean[] completed={false};
  Result result=success->{Runnable finish=()->{if(completed[0])return;completed[0]=true;inFlight=false;if(closed)return;if(revision==generation){if(success){failures=0;publish();}else if(++failures>=3){hasFrame=false;back.invalidate();if(failures==3&&failureListener!=null)failureListener.unavailable();}}if(running&&source!=null)handler.postDelayed(tick,Math.max(0,INTERVAL_MS-(SystemClock.uptimeMillis()-started)));};if(Looper.myLooper()==Looper.getMainLooper())finish.run();else handler.post(finish);};
  try{source.copy(capture,result);}catch(RuntimeException error){result.complete(false);}
 }};
 private void publish(){capture.getPixels(pixels,0,SAMPLE_WIDTH,0,0,SAMPLE_WIDTH,SAMPLE_HEIGHT);if(Build.VERSION.SDK_INT<31){blur(pixels,scratch,SAMPLE_WIDTH,SAMPLE_HEIGHT,Math.max(.2f,BLUR_PX*SAMPLE_WIDTH/Math.max(1,getWidth()*BACK_SCALE)),true);blur(scratch,pixels,SAMPLE_WIDTH,SAMPLE_HEIGHT,Math.max(.2f,BLUR_PX*SAMPLE_HEIGHT/Math.max(1,getHeight()*BACK_SCALE)),false);}display.setPixels(pixels,0,SAMPLE_WIDTH,0,0,SAMPLE_WIDTH,SAMPLE_HEIGHT);hasFrame=true;back.invalidate();}
 private static void blur(int[] input,int[] output,int width,int height,float sigma,boolean horizontal){int radius=Math.min(12,Math.max(1,(int)Math.ceil(sigma*3)));float[] weights=new float[radius*2+1];float total=0;for(int i=-radius;i<=radius;i++){float weight=(float)Math.exp(-i*i/(2*sigma*sigma));weights[i+radius]=weight;total+=weight;}for(int y=0;y<height;y++)for(int x=0;x<width;x++){float red=0,green=0,blue=0;for(int i=-radius;i<=radius;i++){int sx=horizontal?Math.max(0,Math.min(width-1,x+i)):x,sy=horizontal?y:Math.max(0,Math.min(height-1,y+i)),color=input[sy*width+sx];float weight=weights[i+radius]/total;red+=Color.red(color)*weight;green+=Color.green(color)*weight;blue+=Color.blue(color)*weight;}output[y*width+x]=Color.rgb(Math.round(red),Math.round(green),Math.round(blue));}}
 private void refresh(){boolean next=!closed&&isAttachedToWindow()&&isShown()&&getWindowVisibility()==VISIBLE;running=next;handler.removeCallbacks(tick);if(next&&source!=null&&!inFlight)handler.post(tick);}
 @Override protected void onAttachedToWindow(){super.onAttachedToWindow();refresh();}
 @Override protected void onDetachedFromWindow(){running=false;handler.removeCallbacks(tick);generation++;super.onDetachedFromWindow();}
 @Override protected void onWindowVisibilityChanged(int visibility){super.onWindowVisibilityChanged(visibility);if(back!=null)refresh();}
 @Override protected void onVisibilityChanged(View changed,int visibility){super.onVisibilityChanged(changed,visibility);if(back!=null)refresh();}
 private void unbindSurface(){if(surface!=null){if(binding!=null)binding.detach(surface);surface.release();surface=null;}}
 @Override public void close(){checkMain();if(closed)return;closed=true;running=false;generation++;handler.removeCallbacks(tick);source=null;unbindSurface();binding=null;hasFrame=false;back.invalidate();/* PixelCopy may still own capture; let both bitmaps be reclaimed with this view. */}
 private static void checkMain(){if(Looper.myLooper()!=Looper.getMainLooper())throw new IllegalStateException("Use the main thread");}
 private final class AmbientView extends View {
  final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);final RectF destination=new RectF();
  AmbientView(Context context){super(context);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);ColorMatrix saturation=new ColorMatrix();saturation.setSaturation(SATURATION);ColorMatrix light=new ColorMatrix(new float[]{BRIGHTNESS,0,0,0,0,0,BRIGHTNESS,0,0,0,0,0,BRIGHTNESS,0,0,0,0,0,1,0});light.postConcat(saturation);paint.setColorFilter(new ColorMatrixColorFilter(light));}
  @Override protected void onDraw(Canvas canvas){super.onDraw(canvas);if(!hasFrame)return;destination.set(0,0,getWidth(),getHeight());canvas.drawBitmap(display,null,destination,paint);}
 }
}
