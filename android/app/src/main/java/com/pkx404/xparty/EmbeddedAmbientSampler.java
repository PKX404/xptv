package com.pkx404.xparty;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Base64;
import android.view.PixelCopy;
import android.view.WindowManager;
import android.webkit.WebView;
import java.io.ByteArrayOutputStream;
import org.json.JSONObject;

/** Samples only the visible embedded player in this app's own window.
 * No display capture, provider injection, second decoder or full-size bitmap.
 * Protected surfaces remain protected; errors use the ordinary ambient fallback.
 */
final class EmbeddedAmbientSampler {
 interface Listener { void message(JSONObject message); }
 private final Activity activity;
 private final Listener listener;
 private final Handler handler=new Handler(Looper.getMainLooper());
 private final Bitmap bitmap=Bitmap.createBitmap(20,12,Bitmap.Config.ARGB_8888);
 private final ByteArrayOutputStream bytes=new ByteArrayOutputStream(2048);
 private WebView web;
 private String sourceId="";
 private boolean enabled,playing,suspended,inFlight,destroyed;
 private long revision,generation,sequence;
 private int failures;
 private double viewport,x,y,width,height;

 EmbeddedAmbientSampler(Activity activity,Listener listener){this.activity=activity;this.listener=listener;}
 void attach(WebView value){stop();web=value;}
 void configure(JSONObject message){
  handler.removeCallbacks(tick);revision++;
  String id=message.optString("sourceId"),kind=message.optString("kind");
  enabled=message.optBoolean("enabled")&&id.matches("[A-Za-z0-9_-]{1,100}")&&("youtube".equals(kind)||"vimeo".equals(kind)||"twitch".equals(kind));
  generation=message.optLong("generation");sourceId=id;playing=message.optBoolean("playing");failures=0;
  viewport=message.optDouble("viewport");x=message.optDouble("x");y=message.optDouble("y");width=message.optDouble("width");height=message.optDouble("height");
  enabled=enabled&&Double.isFinite(viewport)&&Double.isFinite(x)&&Double.isFinite(y)&&Double.isFinite(width)&&Double.isFinite(height)&&viewport>0&&width>0&&height>0&&width<=viewport*4&&height<=viewport*8;
  if(enabled&&!suspended&&!inFlight)handler.post(tick);
 }
 private boolean visible(){return enabled&&!suspended&&!destroyed&&web!=null&&web.isShown()&&(activity.getWindow().getAttributes().flags&WindowManager.LayoutParams.FLAG_SECURE)==0;}
 private final Runnable tick=new Runnable(){public void run(){
  if(!visible()||inFlight)return;
  int[] origin=new int[2];web.getLocationInWindow(origin);double scale=web.getWidth()/viewport;
  Rect crop=new Rect(origin[0]+(int)Math.round(x*scale),origin[1]+(int)Math.round(y*scale),origin[0]+(int)Math.round((x+width)*scale),origin[1]+(int)Math.round((y+height)*scale));
  if(!crop.intersect(origin[0],origin[1],origin[0]+web.getWidth(),origin[1]+web.getHeight()))return;
  final long epoch=revision,started=SystemClock.uptimeMillis();inFlight=true;
  try{PixelCopy.request(activity.getWindow(),crop,bitmap,result->finish(epoch,started,result),handler);}
  catch(IllegalArgumentException error){finish(epoch,started,PixelCopy.ERROR_SOURCE_INVALID);}
 }};
 private void finish(long epoch,long started,int result){
  inFlight=false;if(destroyed)return;
  if(epoch!=revision){if(visible())handler.post(tick);return;}
  if(!visible())return;
  if(result==PixelCopy.SUCCESS){
   failures=0;bytes.reset();bitmap.compress(Bitmap.CompressFormat.PNG,100,bytes);
   try{listener.message(new JSONObject().put("type","ambient-frame").put("sourceId",sourceId).put("generation",generation).put("sequence",++sequence).put("image","data:image/png;base64,"+Base64.encodeToString(bytes.toByteArray(),Base64.NO_WRAP)));}catch(Exception ignored){}
  }else if(++failures==3){
   try{listener.message(new JSONObject().put("type","ambient-unavailable").put("sourceId",sourceId).put("generation",generation));}catch(Exception ignored){}
  }
  // A paused player is sampled once, then frozen. Never queue overlapping copies.
  if(playing||result!=PixelCopy.SUCCESS&&failures<3)handler.postDelayed(tick,Math.max(0,(failures>=3?1000:100)-(SystemClock.uptimeMillis()-started)));
 }
 void suspend(){suspended=true;revision++;handler.removeCallbacks(tick);}
 void resume(){suspended=false;if(enabled&&!inFlight){handler.removeCallbacks(tick);handler.post(tick);}}
 void stop(){enabled=false;revision++;sourceId="";handler.removeCallbacks(tick);}
 void destroy(){stop();destroyed=true;web=null;/* An outstanding PixelCopy may still own bitmap. */}
}
