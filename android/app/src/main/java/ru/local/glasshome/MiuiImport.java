package ru.local.glasshome;
import android.content.*;import android.content.pm.*;import android.graphics.*;import android.graphics.drawable.*;import android.util.Base64;import java.io.*;import java.util.*;import org.json.*;
final class MiuiImport{
 static void exportCurrent(Home h)throws Exception{write(new File(h.getExternalFilesDir(null),"current-liquid-layout.json"),new JSONObject().put("home_v8",prefs(h.prefs)).put("layout_v8",prefs(h.layout)));}
 static void write(File p,JSONObject json)throws Exception{p.getParentFile().mkdirs();File temp=new File(p.getPath()+".tmp");try(FileOutputStream out=new FileOutputStream(temp)){out.write(json.toString(2).getBytes("UTF-8"));out.getFD().sync();}if(!temp.renameTo(p))throw new IOException("Could not save backup");}
 static JSONObject prefs(android.content.SharedPreferences p)throws Exception{JSONObject j=new JSONObject();for(Map.Entry<String,?> e:p.getAll().entrySet())j.put(e.getKey(),e.getValue());return j;}
 static void apply(Home h)throws Exception{
  File input=new File(h.getExternalFilesDir(null),"miui-layout.json");if(!input.exists())return;
  if(h.layout.getBoolean("miuiImported8",false))return;
  StringBuilder text=new StringBuilder();try(Reader in=new InputStreamReader(new FileInputStream(input),"UTF-8")){char[] b=new char[8192];int n;while((n=in.read(b))>0){text.append(b,0,n);if(text.length()>4000000)throw new IOException("Import too large");}}
  JSONObject data=new JSONObject(text.toString());JSONArray items=data.getJSONArray("items"),screens=data.getJSONArray("screens");if(data.getInt("columns")!=4||data.getInt("rows")!=6||screens.length()<1||screens.length()>12)throw new IOException("Unsupported grid");
  File backup=new File(h.getExternalFilesDir(null),"before-miui-import.json");if(!backup.exists())write(backup,new JSONObject().put("home_v7",prefs(h.getSharedPreferences("home_v7",0))).put("layout_v7",prefs(h.getSharedPreferences("layout_v7",0))));
  Map<Long,String> values=new HashMap<>();Map<Long,Integer> pages=new HashMap<>();JSONArray skipped=new JSONArray();int apps=0,folders=0;android.content.SharedPreferences.Editor le=h.layout.edit().clear(),pe=h.prefs.edit();
  for(int n=0;n<screens.length();n++){pages.put(screens.getJSONObject(n).getLong("_id"),n);le.putBoolean("p"+n+"_music",false).putBoolean("p"+n+"_vpn",false);for(int slot:h.gridSlotsForRows(6))le.putString("p"+n+"_s"+slot,"");}
  for(int i=0;i<items.length();i++){
   JSONObject o=items.getJSONObject(i);long id=o.getLong("_id");int type=o.getInt("itemType");
   if(type==2){String key="folder:miui-"+id;values.put(id,key);le.putString(key+"_name",o.optString("title","Папка"));folders++;continue;}
   if(type!=0&&type!=1)continue;
   try{if(o.optInt("profileId",0)!=0)throw new Exception("other profile");Intent intent=Intent.parseUri(o.getString("intent"),0);String pkg=intent.getComponent()!=null?intent.getComponent().getPackageName():intent.getPackage();if(pkg==null)throw new Exception("no package");ApplicationInfo ai=h.getPackageManager().getApplicationInfo(pkg,0);if(!ai.enabled||h.getPackageManager().resolveActivity(intent,0)==null)throw new Exception("disabled or unavailable");
    String key="entry:miui-"+id;values.put(id,key);le.putString(key+"_package",pkg).putString(key+"_intent",o.getString("intent"));if(intent.getComponent()!=null)le.putString(key+"_component",intent.getComponent().flattenToString());String title=o.isNull("title")?h.getPackageManager().getApplicationLabel(ai).toString():o.getString("title");le.putString(key+"_label",title);
    if(o.has("icon")){File icon=new File(h.getFilesDir(),"miui-icons/"+id+".png");icon.getParentFile().mkdirs();try(FileOutputStream out=new FileOutputStream(icon)){out.write(Base64.decode(o.getString("icon"),Base64.DEFAULT));}le.putString(key+"_icon",icon.getName());}apps++;
   }catch(Exception e){skipped.put(new JSONObject().put("id",id).put("label",o.optString("title","")).put("reason",e.getMessage()==null?"not available":e.getMessage()));}
  }
  for(int i=0;i<items.length();i++){JSONObject folder=items.getJSONObject(i);if(folder.getInt("itemType")!=2)continue;long id=folder.getLong("_id");ArrayList<JSONObject> children=new ArrayList<>();for(int n=0;n<items.length();n++){JSONObject child=items.getJSONObject(n);if(child.getLong("container")==id&&values.containsKey(child.getLong("_id")))children.add(child);}children.sort((a,b)->{int cmp=Integer.compare(a.optInt("screen",0),b.optInt("screen",0));if(cmp==0)cmp=Integer.compare(a.optInt("cellY",0),b.optInt("cellY",0));if(cmp==0)cmp=Integer.compare(a.optInt("cellX",0),b.optInt("cellX",0));return cmp;});JSONArray members=new JSONArray();for(JSONObject child:children)members.put(values.get(child.getLong("_id")));le.putString(values.get(id)+"_apps",members.toString());}
  for(int n=16;n<20;n++)pe.putString("slot"+n,"");
  for(int i=0;i<items.length();i++){JSONObject o=items.getJSONObject(i);long container=o.getLong("container");String value=values.get(o.getLong("_id"));if(container==-101&&value!=null){int x=o.optInt("cellX",o.optInt("screen",0));if(x>=0&&x<4)pe.putString("slot"+(16+x),value);}if(container!=-100)continue;Integer page=pages.get(o.getLong("screen"));if(page==null)continue;int x=o.getInt("cellX"),y=o.getInt("cellY");if(value!=null&&x>=0&&x<4&&y>=0&&y<6)le.putString("p"+page+"_s"+h.gridSlot(y*4+x),value);
   if(o.getInt("itemType")==4||o.getInt("itemType")==5){for(int yy=y;yy<y+o.optInt("spanY",1);yy++)for(int xx=x;xx<x+o.optInt("spanX",1);xx++)if(xx>=0&&xx<4&&yy>=0&&yy<6)le.putBoolean("p"+page+"_blocked_"+h.gridSlot(yy*4+xx),true);if(o.getInt("itemType")==5)le.putBoolean("p"+page+"_miuiClock",true);}
  }
  le.putInt("pageCount",screens.length()).putInt("lastPage",0).putInt("gridRows",6).putBoolean("miuiMode",true).putBoolean("miuiImported8",true).putBoolean("copied_v7",true).putBoolean("v7_migrated",true).putBoolean("v6_panel_created",true).putBoolean("add_hint_done",true);
  pe.putBoolean("miuiIcons",true).putBoolean("colorIcons",true);
  if(!le.commit()||!pe.commit())throw new IOException("Could not commit imported layout");
  write(new File(h.getExternalFilesDir(null),"miui-import-report.json"),new JSONObject().put("source",data.getString("source")).put("pages",screens.length()).put("folders",folders).put("imported_entries",apps).put("skipped",skipped));input.delete();
 }
 static Drawable icon(Home h,String value)throws Exception{if(!value.startsWith("entry:"))return h.getPackageManager().getApplicationIcon(value);String file=h.layout.getString(value+"_icon","");if(!file.isEmpty()){Bitmap b=BitmapFactory.decodeFile(new File(h.getFilesDir(),"miui-icons/"+file).getPath());if(b!=null)return new BitmapDrawable(h.getResources(),b);}String component=h.layout.getString(value+"_component","");if(!component.isEmpty())try{return h.getPackageManager().getActivityIcon(ComponentName.unflattenFromString(component));}catch(Exception ignored){}return h.getPackageManager().getApplicationIcon(h.layout.getString(value+"_package",""));}
}
