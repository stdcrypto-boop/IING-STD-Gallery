package id.std.gallery;
import android.app.*;
import android.os.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.provider.MediaStore;
import android.view.*;
import android.widget.*;
import java.util.*;
import java.util.concurrent.*;
public class MainActivity extends Activity {
 final int BG=0xff101719, ACC=0xff42dcc4;
 final ExecutorService worker=Executors.newSingleThreadExecutor(), thumbs=Executors.newFixedThreadPool(2);
 final android.util.LruCache<String,Bitmap> cache=new android.util.LruCache<String,Bitmap>(24*1024*1024){protected int sizeOf(String k,Bitmap b){return b.getByteCount();}};
 final ArrayList<Item> items=new ArrayList<>(), visible=new ArrayList<>();
 final Set<String> favorites=new HashSet<>();
 GridView grid; TextView status; EditText search; String mode="Semua", album=null; int generation; boolean dead;
 static class Item {Uri uri;String name,folder,mime;long date; Item(Uri u,String n,String f,String m,long d){uri=u;name=n;folder=f;mime=m;date=d;}}
 int dp(int n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
 public void onCreate(Bundle b){super.onCreate(b);favorites.addAll(getPreferences(0).getStringSet("favorites",Collections.emptySet()));build();permissions();}
 void permissions(){ArrayList<String> p=new ArrayList<>();String[] names=Build.VERSION.SDK_INT>=33?new String[]{"android.permission.READ_MEDIA_IMAGES","android.permission.READ_MEDIA_VIDEO","android.permission.READ_MEDIA_AUDIO"}:new String[]{"android.permission.READ_EXTERNAL_STORAGE"};for(String n:names)if(checkSelfPermission(n)!=PackageManager.PERMISSION_GRANTED)p.add(n);if(!p.isEmpty())requestPermissions(p.toArray(new String[0]),1);else load();}
 public void onRequestPermissionsResult(int r,String[] p,int[] g){super.onRequestPermissionsResult(r,p,g);load();}
 Button button(String s,Runnable action){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTextColor(ACC);b.setOnClickListener(v->action.run());return b;}
 void build(){LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setBackgroundColor(BG);root.setPadding(dp(8),dp(8),dp(8),dp(8));root.setOnApplyWindowInsetsListener((v,i)->{v.setPadding(dp(8),i.getSystemWindowInsetTop()+dp(8),dp(8),i.getSystemWindowInsetBottom()+dp(8));return i;});
 TextView title=new TextView(this);title.setText("IING STD  •  GALLERY");title.setTextSize(24);title.setTextColor(ACC);root.addView(title);
 search=new EditText(this);search.setSingleLine();search.setHint("Cari foto, video, audio…");root.addView(search);search.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int f){}public void onTextChanged(CharSequence s,int a,int b,int c){filter();}public void afterTextChanged(android.text.Editable e){}});
 HorizontalScrollView scroll=new HorizontalScrollView(this);LinearLayout tabs=new LinearLayout(this);for(String s:new String[]{"Semua","Foto","Video","Audio","Favorit"})tabs.addView(button(s,()->{mode=s;album=null;filter();}));scroll.addView(tabs);root.addView(scroll);
 status=new TextView(this);root.addView(status);grid=new GridView(this);grid.setNumColumns(3);grid.setHorizontalSpacing(dp(3));grid.setVerticalSpacing(dp(3));root.addView(grid,new LinearLayout.LayoutParams(-1,0,1));grid.setAdapter(new Adapter());grid.setOnItemClickListener((a,v,p,id)->open(visible.get(p)));grid.setOnItemLongClickListener((a,v,p,id)->{options(visible.get(p));return true;});
 LinearLayout nav=new LinearLayout(this);for(String s:new String[]{"Album","Refresh","Buka file","Ukuran"})nav.addView(button(s,()->{switch(s){case "Album":albums();break;case "Refresh":permissions();break;case "Buka file":Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("*/*");i.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(i,2);break;default:new AlertDialog.Builder(this).setItems(new String[]{"2 kolom","3 kolom","4 kolom","5 kolom"},(d,w)->grid.setNumColumns(w+2)).show();}}),new LinearLayout.LayoutParams(0,-2,1));root.addView(nav);setContentView(root);}
 void load(){int token=++generation;status.setText("Memuat media…");worker.execute(()->{ArrayList<Item> found=new ArrayList<>();for(Uri base:new Uri[]{MediaStore.Images.Media.EXTERNAL_CONTENT_URI,MediaStore.Video.Media.EXTERNAL_CONTENT_URI,MediaStore.Audio.Media.EXTERNAL_CONTENT_URI}){try(Cursor c=getContentResolver().query(base,new String[]{"_id","_display_name","bucket_display_name","mime_type","date_modified"},null,null,null)){if(c!=null)while(c.moveToNext())found.add(new Item(ContentUris.withAppendedId(base,c.getLong(0)),c.getString(1),c.getString(2),c.getString(3),c.getLong(4)));}catch(Exception ex){if(base.equals(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI))try(Cursor c=getContentResolver().query(base,new String[]{"_id","_display_name","mime_type","date_modified"},null,null,null)){if(c!=null)while(c.moveToNext())found.add(new Item(ContentUris.withAppendedId(base,c.getLong(0)),c.getString(1),"Audio",c.getString(2),c.getLong(3)));}catch(Exception ignored){}}}found.sort((a,b)->Long.compare(b.date,a.date));runOnUiThread(()->{if(dead||token!=generation)return;items.clear();items.addAll(found);filter();});});}
 void filter(){if(grid==null)return;visible.clear();String q=search.getText().toString().toLowerCase(Locale.ROOT);for(Item i:items){String m=i.mime==null?"":i.mime;if(album!=null&&!album.equals(i.folder))continue;if(mode.equals("Foto")&&!m.startsWith("image/")||mode.equals("Video")&&!m.startsWith("video/")||mode.equals("Audio")&&!m.startsWith("audio/")||mode.equals("Favorit")&&!favorites.contains(i.uri.toString()))continue;if(i.name!=null&&i.name.toLowerCase(Locale.ROOT).contains(q))visible.add(i);}status.setText((album==null?mode:album)+" • "+visible.size()+" item");((BaseAdapter)grid.getAdapter()).notifyDataSetChanged();}
 void albums(){TreeSet<String> folders=new TreeSet<>();for(Item i:items)if(i.folder!=null)folders.add(i.folder);String[] a=folders.toArray(new String[0]);new AlertDialog.Builder(this).setTitle("Album").setItems(a,(d,w)->{album=a[w];mode="Semua";filter();}).show();}
 class Adapter extends BaseAdapter{public int getCount(){return visible.size();}public Object getItem(int p){return visible.get(p);}public long getItemId(int p){return p;}public View getView(int p,View old,android.view.ViewGroup parent){Item i=visible.get(p);LinearLayout box=new LinearLayout(MainActivity.this);box.setOrientation(1);ImageView im=new ImageView(MainActivity.this);im.setScaleType(ImageView.ScaleType.CENTER_CROP);im.setImageResource(android.R.drawable.ic_menu_gallery);box.addView(im,new LinearLayout.LayoutParams(-1,dp(110)));TextView label=new TextView(MainActivity.this);label.setText(i.name);label.setSingleLine(true);label.setEllipsize(android.text.TextUtils.TruncateAt.END);box.addView(label);String key=i.uri.toString();Bitmap hit=cache.get(key);if(hit!=null)im.setImageBitmap(hit);else if(Build.VERSION.SDK_INT>=29&&!thumbs.isShutdown())thumbs.execute(()->{try{Bitmap b=getContentResolver().loadThumbnail(i.uri,new android.util.Size(240,240),null);cache.put(key,b);runOnUiThread(()->{if(!dead)im.setImageBitmap(b);});}catch(Exception ignored){}});return box;}}
 void options(Item i){new AlertDialog.Builder(this).setTitle(i.name).setItems(new String[]{favorites.contains(i.uri.toString())?"Hapus favorit":"Tambah favorit","Bagikan","Info"},(d,w)->{if(w==0){if(!favorites.add(i.uri.toString()))favorites.remove(i.uri.toString());getPreferences(0).edit().putStringSet("favorites",new HashSet<>(favorites)).apply();filter();}else if(w==1){Intent s=new Intent(Intent.ACTION_SEND);s.setType(i.mime==null?"application/octet-stream":i.mime);s.putExtra(Intent.EXTRA_STREAM,i.uri);s.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);startActivity(Intent.createChooser(s,"Bagikan"));}else new AlertDialog.Builder(this).setMessage(i.name+"\n"+i.mime+"\n"+i.folder).setPositiveButton("OK",null).show();}).show();}
 void open(Item i){String m=i.mime==null?"":i.mime;if(m.startsWith("image/")){ImageView im=new ImageView(this);im.setScaleType(ImageView.ScaleType.FIT_CENTER);try{if(Build.VERSION.SDK_INT>=28)im.setImageDrawable(android.graphics.ImageDecoder.decodeDrawable(android.graphics.ImageDecoder.createSource(getContentResolver(),i.uri),(decoder,info,src)->{int w=info.getSize().getWidth(),h=info.getSize().getHeight();decoder.setTargetSampleSize(Math.max(1,Math.max(w,h)/2048));}));else im.setImageURI(i.uri);if(im.getDrawable() instanceof android.graphics.drawable.Animatable)((android.graphics.drawable.Animatable)im.getDrawable()).start();new AlertDialog.Builder(this).setTitle(i.name).setView(im).setPositiveButton("Tutup",null).show();}catch(Exception e){external(i);} }else if(m.startsWith("video/")||m.startsWith("audio/")){playVlc(i);}else external(i);}
 void playVlc(Item i){
 org.videolan.libvlc.LibVLC engine=null;
 org.videolan.libvlc.MediaPlayer player=null;
 try{
 engine=new org.videolan.libvlc.LibVLC(this,new ArrayList<String>());
 player=new org.videolan.libvlc.MediaPlayer(engine);
 final org.videolan.libvlc.LibVLC lib=engine;
 final org.videolan.libvlc.MediaPlayer mp=player;
 LinearLayout panel=new LinearLayout(this);panel.setOrientation(1);
 org.videolan.libvlc.util.VLCVideoLayout surface=new org.videolan.libvlc.util.VLCVideoLayout(this);
 panel.addView(surface,new LinearLayout.LayoutParams(-1,dp(260)));
 SeekBar seek=new SeekBar(this);seek.setMax(1000);panel.addView(seek);
 LinearLayout controls=new LinearLayout(this);controls.addView(button("Play / Pause",()->{if(mp.isPlaying())mp.pause();else mp.play();}));controls.addView(button("Kecepatan",()->{String[] rates={"0.5","1.0","1.5","2.0"};new AlertDialog.Builder(this).setItems(rates,(d,w)->mp.setRate(Float.parseFloat(rates[w]))).show();}));panel.addView(controls);
 AlertDialog dialog=new AlertDialog.Builder(this).setTitle(i.name).setView(panel).setPositiveButton("Tutup",null).create();
 Handler h=new Handler(Looper.getMainLooper());Runnable tick=new Runnable(){public void run(){if(!dialog.isShowing())return;seek.setProgress((int)(mp.getPosition()*1000));h.postDelayed(this,500);}};
 seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar b,int p,boolean user){if(user)mp.setPosition(p/1000f);}public void onStartTrackingTouch(SeekBar b){}public void onStopTrackingTouch(SeekBar b){}});
 dialog.setOnDismissListener(d->{h.removeCallbacks(tick);mp.stop();mp.detachViews();mp.release();lib.release();});
 org.videolan.libvlc.Media media=new org.videolan.libvlc.Media(lib,i.uri);mp.setMedia(media);media.release();
 dialog.show();mp.attachViews(surface,null,false,false);mp.play();h.post(tick);
 }catch(Exception|LinkageError e){if(player!=null)player.release();if(engine!=null)engine.release();external(i);}
 }
 void external(Item i){try{Intent v=new Intent(Intent.ACTION_VIEW);v.setDataAndType(i.uri,i.mime==null?"application/octet-stream":i.mime);v.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);startActivity(v);}catch(Exception e){Toast.makeText(this,"Format belum didukung pemutar perangkat",Toast.LENGTH_LONG).show();}}
 protected void onActivityResult(int r,int c,Intent d){super.onActivityResult(r,c,d);if(r==2&&c==RESULT_OK&&d!=null&&d.getData()!=null){Uri u=d.getData();open(new Item(u,"File pilihan","",getContentResolver().getType(u),0));}}
 protected void onDestroy(){dead=true;worker.shutdownNow();thumbs.shutdownNow();super.onDestroy();}
}
