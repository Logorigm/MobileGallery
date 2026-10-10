package com.example.offlinemediavault;

import android.app.*;
import android.os.*;
import android.content.*;
import android.database.Cursor;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.media.MediaMetadataRetriever;
import android.util.LruCache;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import android.provider.OpenableColumns;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.util.*;
import java.util.zip.*;

public class MainActivity extends Activity {
    private final int BG=0xFF18191D, PANEL=0xFF25272E, FIELD=0xFF343741, FG=0xFFF0F0F4, MUTED=0xFFA7AAB5, ACCENT=0xFF777FE8;
    private LinearLayout root, listBox;
    private EditText search;
    private ArrayList<MediaItem> items = new ArrayList<>();
    private File mediaDir;
    private static final int PICK_MEDIA=101, PICK_ZIP=102;

    static class MediaItem {
        String name, path, tags; long added;
        MediaItem(String n,String p,String t){name=n;path=p;tags=t==null?"":t;}
    }

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(BG); getWindow().setNavigationBarColor(BG);
        mediaDir=new File(getFilesDir(),"media"); if(!mediaDir.exists()) mediaDir.mkdirs();
        load();
        buildUi();
        buildShell();
    }

    private void buildUi() {
        ScrollView outer=new ScrollView(this); outer.setFillViewport(true); outer.setBackgroundColor(BG);
        root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(dp(16),dp(16),dp(16),dp(18)); root.setBackgroundColor(BG);
        outer.addView(root); libraryView=outer;

        TextView title=new TextView(this); title.setText("ЗАГРУЗКА"); title.setTextColor(FG); title.setTextSize(21); title.setTypeface(null,1);
        root.addView(title,params(-1,-2)); TextView subtitle=new TextView(this); subtitle.setText("Загрузка файлов, теги и управление"); subtitle.setTextColor(MUTED); subtitle.setTextSize(13);
        LinearLayout.LayoutParams sp=params(-1,-2); sp.topMargin=dp(4); sp.bottomMargin=dp(16); root.addView(subtitle,sp);

        search=new EditText(this); search.setSingleLine(true); search.setHint("Поиск по названию или тегам"); styleEdit(search);
        root.addView(search,params(-1,dp(48))); search.addTextChangedListener(new android.text.TextWatcher(){
            public void beforeTextChanged(CharSequence s,int st,int c,int a){} public void onTextChanged(CharSequence s,int st,int before,int count){render();}
            public void afterTextChanged(android.text.Editable e){}
        });
        LinearLayout buttons=new LinearLayout(this); buttons.setOrientation(LinearLayout.HORIZONTAL);
        TextView add=button("+ Файлы"), zip=button("Импорт ZIP");
        buttons.addView(add,new LinearLayout.LayoutParams(0,dp(48),1)); LinearLayout.LayoutParams zp=params(0,dp(48)); zp.weight=1; zp.leftMargin=dp(8); buttons.addView(zip,zp);
        LinearLayout.LayoutParams bp=params(-1,-2); bp.topMargin=dp(10); bp.bottomMargin=dp(14); root.addView(buttons,bp);
        add.setOnClickListener(v->pickMedia()); zip.setOnClickListener(v->pickZip());

        TextView label=new TextView(this); label.setText("ЗАГРУЖЕННЫЕ ФАЙЛЫ"); label.setTextColor(MUTED); label.setTextSize(12); label.setTypeface(null,1);
        LinearLayout.LayoutParams lp=params(-1,-2); lp.bottomMargin=dp(8); root.addView(label,lp);
        listBox=new LinearLayout(this); listBox.setOrientation(LinearLayout.VERTICAL); root.addView(listBox,params(-1,-2));
        TextView foot=new TextView(this); foot.setText("Файлы сохраняются только на этом устройстве."); foot.setTextColor(MUTED); foot.setTextSize(12);
        LinearLayout.LayoutParams fp=params(-1,-2); fp.topMargin=dp(16); root.addView(foot,fp);
    }

    private void render() {
        if(listBox==null)return; listBox.removeAllViews();
        String q=search==null?"":search.getText().toString().trim().toLowerCase(Locale.ROOT);
        int count=0;
        for(MediaItem it:new ArrayList<>(items)) {
            if(!q.isEmpty() && !it.name.toLowerCase(Locale.ROOT).contains(q) && !it.tags.toLowerCase(Locale.ROOT).contains(q)) continue;
            count++;
            LinearLayout card=new LinearLayout(this); card.setOrientation(LinearLayout.HORIZONTAL); card.setGravity(Gravity.CENTER_VERTICAL); card.setPadding(dp(10),dp(9),dp(8),dp(9)); card.setBackground(round(PANEL,14));
            LinearLayout.LayoutParams cp=params(-1,-2); cp.bottomMargin=dp(8); listBox.addView(card,cp);
            FrameLayout thumb=makeThumb(it);
            card.addView(thumb,new LinearLayout.LayoutParams(dp(88),dp(88)));
            LinearLayout details=new LinearLayout(this); details.setOrientation(LinearLayout.VERTICAL); details.setPadding(dp(10),0,dp(4),0);
            card.addView(details,new LinearLayout.LayoutParams(0,-2,1));
            TextView name=new TextView(this); name.setText(it.name); name.setTextColor(FG); name.setTextSize(14); name.setMaxLines(2); name.setEllipsize(android.text.TextUtils.TruncateAt.END); name.setTypeface(null,1); details.addView(name);
            TextView meta=new TextView(this); meta.setText((isVideo(it.name)?"Видео · ":"Фото · ")+fmtSize(new File(it.path).length())); meta.setTextColor(ACCENT); meta.setTextSize(11); details.addView(meta);
            TextView tag=new TextView(this); tag.setText(it.tags.isEmpty()?"Нажми ✎, чтобы добавить теги":"# "+it.tags); tag.setTextColor(MUTED); tag.setTextSize(12); tag.setMaxLines(2); tag.setEllipsize(android.text.TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams tp=params(-1,-2); tp.topMargin=dp(4); details.addView(tag,tp);
            TextView edit=new TextView(this); edit.setText("✎"); edit.setTextSize(22); edit.setTextColor(FG); edit.setGravity(Gravity.CENTER); card.addView(edit,new LinearLayout.LayoutParams(dp(40),dp(48)));
            card.setOnClickListener(v->preview(it)); thumb.setOnClickListener(v->preview(it)); name.setOnClickListener(v->preview(it));
            edit.setOnClickListener(v->editTags(it));
            card.setOnLongClickListener(v->{new AlertDialog.Builder(this).setTitle("Удалить файл?").setMessage(it.name+"\nФайл будет удалён с устройства.").setNegativeButton("Отмена",null).setPositiveButton("Удалить",(d,w)->deleteItem(it)).show();return true;});
        }
        if(count==0){TextView empty=new TextView(this);empty.setText(items.isEmpty()?"Пока пусто. Добавь фото, видео или ZIP-архив.":"Ничего не найдено. Попробуй другие теги.");empty.setTextColor(MUTED);empty.setTextSize(14);empty.setGravity(Gravity.CENTER);empty.setPadding(dp(16),dp(32),dp(16),dp(32));empty.setBackground(round(PANEL,14));listBox.addView(empty,params(-1,-2));}
    }

    private void pickMedia() {
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT); i.setType("*/*"); i.putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"image/*","video/*"}); i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true); i.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(i,PICK_MEDIA);
    }
    private void pickZip() {
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT); i.setType("application/zip"); i.addCategory(Intent.CATEGORY_OPENABLE); startActivityForResult(i,PICK_ZIP);
    }
    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data) {
        super.onActivityResult(requestCode,resultCode,data); if(resultCode!=RESULT_OK||data==null)return;
        if(requestCode==PICK_MEDIA) {
            int added=0;
            if(data.getClipData()!=null) {
                for(int n=0;n<data.getClipData().getItemCount();n++) { Uri u=data.getClipData().getItemAt(n).getUri(); if(importUri(u))added++; }
            } else if(data.getData()!=null && importUri(data.getData())) added++;
            save(); refresh(); toast("Добавлено файлов: "+added);
        } else if(requestCode==PICK_ZIP && data.getData()!=null) {
            try { int n=importZip(data.getData()); save(); refresh(); toast("Импортировано медиафайлов: "+n); }
            catch(Exception e){toast("Не удалось прочитать ZIP: "+e.getMessage());}
        }
    }
    private boolean importUri(Uri uri) {
        String name=getName(uri); if(!isMedia(name)) {toast("Пропущен неподдерживаемый файл: "+name);return false;}
        try(InputStream in=getContentResolver().openInputStream(uri)) {
            File out=unique(name); copy(in,new FileOutputStream(out)); MediaItem m=new MediaItem(name,out.getAbsolutePath(),""); m.added=nextStamp(); items.add(m); return true;
        } catch(Exception e){toast("Не удалось импортировать "+name);return false;}
    }
    private int importZip(Uri uri) throws Exception {
        int count=0;
        try(ZipInputStream zin=new ZipInputStream(getContentResolver().openInputStream(uri))) {
            ZipEntry entry;
            while((entry=zin.getNextEntry())!=null) {
                if(!entry.isDirectory()) {
                    String name=new File(entry.getName()).getName();
                    if(isMedia(name)) {
                        File out=unique(name); try(FileOutputStream fout=new FileOutputStream(out)){byte[] buf=new byte[8192];int n;while((n=zin.read(buf))>0)fout.write(buf,0,n);}
                        MediaItem m=new MediaItem(name,out.getAbsolutePath(),""); m.added=nextStamp(); items.add(m);count++;
                    }
                }
                zin.closeEntry();
            }
        }
        return count;
    }
    private void preview(MediaItem it) {
        File f=new File(it.path); if(!f.exists()){toast("Файл не найден");return;}
        if(isVideo(it.name)) {
            Dialog dialog=new Dialog(this); LinearLayout box=new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(dp(12),dp(12),dp(12),dp(12)); box.setBackgroundColor(BG);
            VideoView video=new VideoView(this); MediaController mc=new MediaController(this); mc.setAnchorView(video); video.setMediaController(mc); video.setVideoPath(f.getAbsolutePath());
            box.addView(video,new LinearLayout.LayoutParams(-1,(int)(getResources().getDisplayMetrics().heightPixels*.6)));
            TextView close=button("Закрыть"); box.addView(close,params(-1,dp(46))); close.setOnClickListener(v->{mc.hide();video.stopPlayback();dialog.dismiss();});
            dialog.setOnDismissListener(d->{mc.hide();video.stopPlayback();});
            dialog.setContentView(box); Window w=dialog.getWindow(); if(w!=null){w.setBackgroundDrawableResource(android.R.color.transparent);w.setLayout(-1,-2);} dialog.show(); if(dialog.getWindow()!=null)dialog.getWindow().setLayout(-1,-2); video.start();
        } else {
            Dialog dialog=new Dialog(this); LinearLayout box=new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(dp(10),dp(10),dp(10),dp(10)); box.setBackgroundColor(BG);
            ImageView image=new ImageView(this); image.setAdjustViewBounds(true); image.setScaleType(ImageView.ScaleType.FIT_CENTER);
            android.graphics.Bitmap bitmap=decodeSampled(f.getAbsolutePath(),2048);
            if(bitmap!=null)image.setImageBitmap(bitmap); else {toast("Формат фото не поддерживается для предпросмотра");return;}
            box.addView(image,new LinearLayout.LayoutParams(-1,0,1));
            TextView close=button("Закрыть"); box.addView(close,params(-1,dp(46))); close.setOnClickListener(v->dialog.dismiss());
            dialog.setContentView(box); dialog.show(); if(dialog.getWindow()!=null)dialog.getWindow().setLayout(-1,(int)(getResources().getDisplayMetrics().heightPixels*.85));
        }
    }
    private void editTags(MediaItem it) {
        EditText input=new EditText(this); input.setText(it.tags); input.setTextColor(FG); input.setHintTextColor(MUTED); input.setHint("например: horror, animation, tiktok"); input.setSingleLine(false);
        input.setPadding(dp(12),dp(10),dp(12),dp(10)); input.setBackground(round(FIELD,10));
        LinearLayout box=new LinearLayout(this); box.setPadding(dp(20),dp(8),dp(20),0); box.addView(input,params(-1,dp(56)));
        new AlertDialog.Builder(this).setTitle("Теги для файла").setMessage(it.name).setView(box).setNegativeButton("Отмена",null).setPositiveButton("Сохранить",(d,w)->{it.tags=input.getText().toString().trim().replace("\n",", ");save();refresh();}).show();
    }
    private void deleteItem(MediaItem it) {items.remove(it);new File(it.path).delete();save();refresh();}
    private String getName(Uri uri) {
        String result=null; try(Cursor c=getContentResolver().query(uri,null,null,null,null)){if(c!=null&&c.moveToFirst()){int ix=c.getColumnIndex(OpenableColumns.DISPLAY_NAME);if(ix>=0)result=c.getString(ix);}}
        if(result==null)result=uri.getLastPathSegment(); if(result==null)result="media_"+System.currentTimeMillis(); return new File(result).getName();
    }
    private File unique(String name) {
        String clean=new File(name).getName(),base=clean,ext="";int dot=clean.lastIndexOf('.');if(dot>0){base=clean.substring(0,dot);ext=clean.substring(dot);}
        File f=new File(mediaDir,clean);int n=1;while(f.exists())f=new File(mediaDir,base+"_"+(n++)+ext);return f;
    }
    private boolean isMedia(String name) {String s=name.toLowerCase(Locale.ROOT);return s.matches(".*\\.(jpg|jpeg|png|gif|bmp|webp|mp4|mov|m4v|3gp|mkv|webm|avi)$");}
    private boolean isVideo(String name) {return name.toLowerCase(Locale.ROOT).matches(".*\\.(mp4|mov|m4v|3gp|mkv|webm|avi)$");}
    private void save() {
        try {JSONArray a=new JSONArray();for(MediaItem it:items){JSONObject o=new JSONObject();o.put("name",it.name);o.put("path",it.path);o.put("tags",it.tags);o.put("added",it.added);a.put(o);}getSharedPreferences("library",0).edit().putString("items",a.toString()).apply();}
        catch(Exception ignored){}
    }
    private void load() {
        try {JSONArray a=new JSONArray(getSharedPreferences("library",0).getString("items","[]"));for(int i=0;i<a.length();i++){JSONObject o=a.getJSONObject(i);File f=new File(o.getString("path"));if(f.exists()){MediaItem m=new MediaItem(o.getString("name"),o.getString("path"),o.optString("tags",""));m.added=o.optLong("added",f.lastModified());if(m.added>lastStamp)lastStamp=m.added;items.add(m);}}}
        catch(Exception ignored){}
    }
    private void copy(InputStream in,OutputStream out)throws IOException {try(OutputStream o=out){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)o.write(b,0,n);}}
    private TextView button(String text) {TextView b=new TextView(this);b.setText(text);b.setTextColor(FG);b.setTextSize(14);b.setTypeface(null,1);b.setGravity(Gravity.CENTER);b.setBackground(round(0xFF414552,12));b.setPadding(dp(8),0,dp(8),0);return b;}
    private GradientDrawable round(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    private LinearLayout.LayoutParams params(int w,int h){return new LinearLayout.LayoutParams(w<0?w:dp(w),h<0?h:dp(h));}
    private int dp(int n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
    private void styleEdit(EditText e){e.setTextColor(FG);e.setHintTextColor(MUTED);e.setTextSize(14);e.setPadding(dp(12),0,dp(12),0);e.setBackground(round(FIELD,12));}


    // ---------- Вкладки: Загрузка / Лента ----------
    private View libraryView, browseView;
    private TextView tabLib, tabBrowse, filterBtn, feedInfo;
    private ScrollView feedScroll;
    private LinearLayout browseGrid;
    private final LinkedHashSet<String> incTags=new LinkedHashSet<>(), excTags=new LinkedHashSet<>();
    private boolean matchAll=true, newestFirst=true;
    private long lastStamp=0;
    private static final int RED=0xFFD64550;

    private long nextStamp(){long n=System.currentTimeMillis();if(n<=lastStamp)n=lastStamp+1;lastStamp=n;return n;}
    private void refresh(){render();renderBrowse();}

    // Контейнер, который переносит чипы на новую строку
    static class Flow extends ViewGroup {
        final int hGap,vGap;
        Flow(Context c,int h,int v){super(c);hGap=h;vGap=v;}
        @Override protected void onMeasure(int wSpec,int hSpec){
            int maxW=MeasureSpec.getSize(wSpec),x=0,y=0,rowH=0;
            for(int i=0;i<getChildCount();i++){
                View c=getChildAt(i); if(c.getVisibility()==GONE)continue;
                c.measure(MeasureSpec.makeMeasureSpec(maxW,MeasureSpec.AT_MOST),MeasureSpec.makeMeasureSpec(0,MeasureSpec.UNSPECIFIED));
                int w=c.getMeasuredWidth(),h=c.getMeasuredHeight();
                if(x>0&&x+w>maxW){x=0;y+=rowH+vGap;rowH=0;}
                x+=w+hGap; rowH=Math.max(rowH,h);
            }
            setMeasuredDimension(maxW,y+rowH);
        }
        @Override protected void onLayout(boolean changed,int l,int t,int r,int b){
            int maxW=r-l,x=0,y=0,rowH=0;
            for(int i=0;i<getChildCount();i++){
                View c=getChildAt(i); if(c.getVisibility()==GONE)continue;
                int w=c.getMeasuredWidth(),h=c.getMeasuredHeight();
                if(x>0&&x+w>maxW){x=0;y+=rowH+vGap;rowH=0;}
                c.layout(x,y,x+w,y+h); x+=w+hGap; rowH=Math.max(rowH,h);
            }
        }
    }

    private void buildShell() {
        browseView=buildBrowse();
        LinearLayout shell=new LinearLayout(this); shell.setOrientation(LinearLayout.VERTICAL); shell.setBackgroundColor(BG);
        shell.setOnApplyWindowInsetsListener((v,insets)->{v.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());return insets;});
        FrameLayout body=new FrameLayout(this);
        body.addView(libraryView,new FrameLayout.LayoutParams(-1,-1)); body.addView(browseView,new FrameLayout.LayoutParams(-1,-1));
        shell.addView(body,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout bar=new LinearLayout(this); bar.setBackgroundColor(PANEL);
        tabLib=tab("Загрузка"); tabBrowse=tab("Лента");
        bar.addView(tabLib,new LinearLayout.LayoutParams(0,-1,1)); bar.addView(tabBrowse,new LinearLayout.LayoutParams(0,-1,1));
        shell.addView(bar,new LinearLayout.LayoutParams(-1,dp(54)));
        tabLib.setOnClickListener(v->showTab(0)); tabBrowse.setOnClickListener(v->showTab(1));
        setContentView(shell);
        render(); showTab(items.isEmpty()?0:1);
    }
    private TextView tab(String text){TextView t=new TextView(this);t.setText(text);t.setTextSize(14);t.setTypeface(null,1);t.setGravity(Gravity.CENTER);return t;}
    private void showTab(int t) {
        libraryView.setVisibility(t==0?View.VISIBLE:View.GONE); browseView.setVisibility(t==1?View.VISIBLE:View.GONE);
        tabLib.setTextColor(t==0?ACCENT:MUTED); tabBrowse.setTextColor(t==1?ACCENT:MUTED);
        if(t==1) renderBrowse();
    }

    private View buildBrowse() {
        LinearLayout r=new LinearLayout(this); r.setOrientation(LinearLayout.VERTICAL); r.setBackgroundColor(BG);
        LinearLayout head=new LinearLayout(this); head.setOrientation(LinearLayout.HORIZONTAL); head.setGravity(Gravity.CENTER_VERTICAL); head.setPadding(dp(16),dp(12),dp(12),dp(4));
        TextView title=new TextView(this); title.setText("ЛЕНТА"); title.setTextColor(FG); title.setTextSize(21); title.setTypeface(null,1);
        head.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        filterBtn=new TextView(this); filterBtn.setTextColor(0xFFFFFFFF); filterBtn.setTextSize(14); filterBtn.setTypeface(null,1); filterBtn.setGravity(Gravity.CENTER); filterBtn.setPadding(dp(18),0,dp(18),0);
        filterBtn.setOnClickListener(v->openFilter());
        head.addView(filterBtn,new LinearLayout.LayoutParams(-2,dp(40)));
        r.addView(head,new LinearLayout.LayoutParams(-1,-2));
        feedInfo=new TextView(this); feedInfo.setTextColor(MUTED); feedInfo.setTextSize(12); feedInfo.setPadding(dp(16),0,dp(16),dp(8));
        r.addView(feedInfo,new LinearLayout.LayoutParams(-1,-2));
        feedScroll=new ScrollView(this); browseGrid=new LinearLayout(this); browseGrid.setOrientation(LinearLayout.VERTICAL); browseGrid.setPadding(dp(12),0,dp(12),dp(8));
        feedScroll.addView(browseGrid);
        r.addView(feedScroll,new LinearLayout.LayoutParams(-1,0,1));
        feedScroll.addOnLayoutChangeListener((vw,a1,a2,a3,a4,a5,a6,a7,a8)->{ if((a4-a2)!=(a8-a6)||(a3-a1)!=(a7-a5)) feedScroll.post(this::renderBrowse); });
        return r;
    }

    private TextView chip(String text,boolean on){return chip(text,on,ACCENT);}
    private TextView chip(String text,boolean on,int onColor) {
        TextView c=new TextView(this); c.setText(text); c.setTextSize(13); c.setTextColor(on?0xFFFFFFFF:FG); c.setPadding(dp(14),dp(8),dp(14),dp(8)); c.setBackground(round(on?onColor:FIELD,18)); return c;
    }
    private List<String> tagsOf(MediaItem it) {
        ArrayList<String> out=new ArrayList<>();
        for(String part:it.tags.split("[,;\\n]")) {
            String t=part.trim().toLowerCase(Locale.ROOT); while(t.startsWith("#")) t=t.substring(1).trim();
            if(!t.isEmpty()&&!out.contains(t)) out.add(t);
        }
        return out;
    }
    private TreeMap<String,Integer> allTagCounts() {
        TreeMap<String,Integer> counts=new TreeMap<>();
        for(MediaItem it:items) for(String t:tagsOf(it)) counts.put(t,counts.containsKey(t)?counts.get(t)+1:1);
        return counts;
    }
    // Лента: теги (показать только с ними) минус чёрные теги, сортировка по времени загрузки
    private ArrayList<MediaItem> filtered() {
        ArrayList<MediaItem> out=new ArrayList<>();
        for(MediaItem it:items) {
            List<String> ts=tagsOf(it);
            boolean banned=false; for(String x:excTags) if(ts.contains(x)){banned=true;break;}
            if(banned) continue;
            boolean ok;
            if(incTags.isEmpty()) ok=true;
            else if(matchAll) ok=ts.containsAll(incTags);
            else { ok=false; for(String x:incTags) if(ts.contains(x)){ok=true;break;} }
            if(ok) out.add(it);
        }
        Collections.sort(out,(m1,m2)->newestFirst?Long.compare(m2.added,m1.added):Long.compare(m1.added,m2.added));
        return out;
    }

    private void renderBrowse() {
        if(browseGrid==null||browseView==null||browseView.getVisibility()!=View.VISIBLE) return;
        TreeMap<String,Integer> counts=allTagCounts();
        incTags.retainAll(counts.keySet()); excTags.retainAll(counts.keySet());
        int active=incTags.size()+excTags.size()+(newestFirst?0:1);
        filterBtn.setText(active>0?"Фильтр · "+active:"Фильтр"); filterBtn.setBackground(round(active>0?ACCENT:0xFF414552,20));

        final ArrayList<MediaItem> list=filtered();
        StringBuilder sb=new StringBuilder(); sb.append(list.size()).append(" из ").append(items.size());
        if(!incTags.isEmpty()) sb.append(" · с тегами: ").append(android.text.TextUtils.join(", ",incTags));
        if(!excTags.isEmpty()) sb.append(" · скрыто: ").append(android.text.TextUtils.join(", ",excTags));
        if(!newestFirst) sb.append(" · старые сверху");
        feedInfo.setText(sb.toString());

        browseGrid.removeAllViews();
        if(list.isEmpty()) {
            TextView empty=new TextView(this); empty.setText(items.isEmpty()?"Пока пусто. Загрузи файлы на вкладке «Загрузка».":"Нет файлов с такими фильтрами."); empty.setTextColor(MUTED); empty.setTextSize(14); empty.setGravity(Gravity.CENTER);
            empty.setPadding(dp(16),dp(32),dp(16),dp(32)); empty.setBackground(round(PANEL,14)); browseGrid.addView(empty,params(-1,-2)); return;
        }
        int cols=2, gap=dp(8);
        int sw=feedScroll.getWidth(); if(sw<=0) sw=getResources().getDisplayMetrics().widthPixels;
        int vh=feedScroll.getHeight(); if(vh<=0) vh=getResources().getDisplayMetrics().heightPixels-dp(54)-dp(150);
        int cellW=(sw-dp(24)-gap*(cols-1))/cols;
        int cellH=Math.max(dp(110),(vh-dp(8)-gap*2)/3);   // на экран помещается 3 ряда по 2 = 6 файлов
        LinearLayout row=null;
        for(int i=0;i<list.size();i++) {
            if(i%cols==0){row=new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL); LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,-2); rp.bottomMargin=gap; browseGrid.addView(row,rp);}
            final int idx=i; FrameLayout cell=makeThumb(list.get(i));
            LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(cellW,cellH); if(i%cols!=0) cp.leftMargin=gap; row.addView(cell,cp);
            cell.setOnClickListener(v->openViewer(list,idx));
        }
    }

    // ---------- Фильтр ленты: теги, чёрные теги, сортировка ----------
    private TextView sectionLabel(String t){TextView l=new TextView(this);l.setText(t);l.setTextColor(MUTED);l.setTextSize(12);l.setTypeface(null,1);l.setPadding(0,dp(12),0,dp(8));return l;}
    private void openFilter() {
        final LinkedHashSet<String> inc=new LinkedHashSet<>(incTags), exc=new LinkedHashSet<>(excTags);
        final boolean[] st={matchAll,newestFirst};
        final TreeMap<String,Integer> counts=allTagCounts();
        final LinearLayout box=new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(dp(18),dp(4),dp(18),dp(10));
        ScrollView sv=new ScrollView(this); sv.addView(box);
        final Runnable[] draw=new Runnable[1];
        draw[0]=()->{
            box.removeAllViews();
            box.addView(sectionLabel("ТЕГИ · показывать только с ними"));
            if(counts.isEmpty()) {
                TextView h=new TextView(this); h.setText("Тегов пока нет. Добавь их на вкладке «Загрузка» (кнопка ✎)."); h.setTextColor(MUTED); h.setTextSize(13); box.addView(h);
            } else {
                Flow f1=new Flow(this,dp(8),dp(8));
                for(Map.Entry<String,Integer> e:counts.entrySet()) {
                    final String tag=e.getKey(); TextView c=chip("#"+tag+" ("+e.getValue()+")",inc.contains(tag));
                    c.setOnClickListener(v->{if(!inc.remove(tag)){inc.add(tag);exc.remove(tag);}draw[0].run();}); f1.addView(c);
                }
                box.addView(f1,new LinearLayout.LayoutParams(-1,-2));
                if(inc.size()>1) {
                    TextView mt=new TextView(this); mt.setTextColor(ACCENT); mt.setTextSize(12); mt.setPadding(0,dp(8),0,0);
                    mt.setText("Совпадение: "+(st[0]?"все выбранные теги":"любой из выбранных тегов")+" (нажми, чтобы сменить)");
                    mt.setOnClickListener(v->{st[0]=!st[0];draw[0].run();}); box.addView(mt);
                }
                box.addView(sectionLabel("ЧЁРНЫЕ ТЕГИ · скрывать файлы с ними"));
                Flow f2=new Flow(this,dp(8),dp(8));
                for(Map.Entry<String,Integer> e:counts.entrySet()) {
                    final String tag=e.getKey(); TextView c=chip("#"+tag,exc.contains(tag),RED);
                    c.setOnClickListener(v->{if(!exc.remove(tag)){exc.add(tag);inc.remove(tag);}draw[0].run();}); f2.addView(c);
                }
                box.addView(f2,new LinearLayout.LayoutParams(-1,-2));
            }
            box.addView(sectionLabel("СОРТИРОВКА ПО ВРЕМЕНИ ЗАГРУЗКИ"));
            Flow f3=new Flow(this,dp(8),dp(8));
            TextView n1=chip("Сначала новые",st[1]), n2=chip("Сначала старые",!st[1]);
            n1.setOnClickListener(v->{st[1]=true;draw[0].run();}); n2.setOnClickListener(v->{st[1]=false;draw[0].run();});
            f3.addView(n1); f3.addView(n2); box.addView(f3,new LinearLayout.LayoutParams(-1,-2));
        };
        draw[0].run();
        new AlertDialog.Builder(this).setTitle("Фильтр ленты").setView(sv)
            .setNegativeButton("Отмена",null)
            .setNeutralButton("Сбросить",(d,w)->{incTags.clear();excTags.clear();matchAll=true;newestFirst=true;renderBrowse();})
            .setPositiveButton("Применить",(d,w)->{incTags.clear();incTags.addAll(inc);excTags.clear();excTags.addAll(exc);matchAll=st[0];newestFirst=st[1];renderBrowse();})
            .show();
    }

    // ---------- Полноэкранный просмотр со свайпом ----------
    private void stopMedia(FrameLayout media){for(int i=0;i<media.getChildCount();i++){View v=media.getChildAt(i);if(v instanceof VideoView)((VideoView)v).stopPlayback();}}
    private TextView viewerBtn(String t){TextView b=new TextView(this);b.setText(t);b.setTextColor(FG);b.setTextSize(20);b.setGravity(Gravity.CENTER);return b;}
    private void openViewer(final ArrayList<MediaItem> list,int start) {
        final int[] idx={start}; final int[] token={0};
        final Runnable[] show=new Runnable[1]; final MediaController[] mcRef=new MediaController[1];
        final GestureDetector gd=new GestureDetector(this,new GestureDetector.SimpleOnGestureListener(){
            @Override public boolean onFling(MotionEvent e1,MotionEvent e2,float vx,float vy){
                if(e1==null||e2==null||list.size()<2) return false;
                float dx=e2.getX()-e1.getX(), dy=e2.getY()-e1.getY();
                if(Math.abs(dx)>dp(80)&&Math.abs(dx)>Math.abs(dy)*1.5f){idx[0]=(idx[0]+(dx<0?1:list.size()-1))%list.size();show[0].run();return true;}
                return false;
            }
        });
        final Dialog dialog=new Dialog(this,android.R.style.Theme_Black_NoTitleBar_Fullscreen){
            @Override public boolean dispatchTouchEvent(MotionEvent ev){gd.onTouchEvent(ev);return super.dispatchTouchEvent(ev);}
        };
        final FrameLayout media=new FrameLayout(this);
        FrameLayout rootF=new FrameLayout(this); rootF.setBackgroundColor(0xFF000000); rootF.addView(media,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout top=new LinearLayout(this); top.setOrientation(LinearLayout.VERTICAL); top.setBackgroundColor(0xB3000000); top.setPadding(dp(8),dp(8),dp(8),dp(8));
        LinearLayout bar=new LinearLayout(this); bar.setGravity(Gravity.CENTER_VERTICAL);
        TextView close=viewerBtn("✕"), prev=viewerBtn("◀"), next=viewerBtn("▶");
        final TextView title=new TextView(this); title.setTextColor(FG); title.setTextSize(13); title.setMaxLines(2); title.setEllipsize(android.text.TextUtils.TruncateAt.END); title.setPadding(dp(8),0,dp(8),0);
        final TextView tagsTv=new TextView(this); tagsTv.setTextColor(MUTED); tagsTv.setTextSize(12); tagsTv.setPadding(dp(52),dp(2),dp(8),0);
        bar.addView(close,new LinearLayout.LayoutParams(dp(44),dp(44))); bar.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        if(list.size()>1){bar.addView(prev,new LinearLayout.LayoutParams(dp(44),dp(44))); bar.addView(next,new LinearLayout.LayoutParams(dp(44),dp(44)));}
        top.addView(bar); top.addView(tagsTv);
        rootF.addView(top,new FrameLayout.LayoutParams(-1,-2,Gravity.TOP));
        close.setOnClickListener(v->dialog.dismiss());
        prev.setOnClickListener(v->{idx[0]=(idx[0]+list.size()-1)%list.size();show[0].run();});
        next.setOnClickListener(v->{idx[0]=(idx[0]+1)%list.size();show[0].run();});
        show[0]=()->{
            token[0]++; final int my=token[0];
            if(mcRef[0]!=null) mcRef[0].hide(); stopMedia(media); media.removeAllViews();
            MediaItem it=list.get(idx[0]);
            title.setText((idx[0]+1)+" / "+list.size()+"  ·  "+it.name);
            List<String> ts=tagsOf(it); tagsTv.setText(ts.isEmpty()?"Без тегов":"# "+android.text.TextUtils.join(", ",ts));
            final File f=new File(it.path); if(!f.exists()){toast("Файл не найден");return;}
            if(isVideo(it.name)) {
                VideoView vv=new VideoView(this); MediaController mc=new MediaController(this); mc.setAnchorView(vv); vv.setMediaController(mc); mcRef[0]=mc;
                vv.setVideoPath(f.getAbsolutePath()); media.addView(vv,new FrameLayout.LayoutParams(-1,-1,Gravity.CENTER)); vv.start();
            } else {
                final ImageView iv=new ImageView(this); iv.setScaleType(ImageView.ScaleType.FIT_CENTER); media.addView(iv,new FrameLayout.LayoutParams(-1,-1));
                pool.execute(()->{Bitmap b=decodeSampled(f.getAbsolutePath(),2048); ui.post(()->{if(my==token[0]){if(b!=null)iv.setImageBitmap(b);else toast("Формат фото не поддерживается для предпросмотра");}});});
            }
        };
        dialog.setContentView(rootF);
        dialog.setOnDismissListener(d->{if(mcRef[0]!=null)mcRef[0].hide();stopMedia(media);});
        dialog.show(); show[0].run();
    }

    // ---------- Миниатюры ----------
    private static class Thumb { Bitmap bmp; long dur; }
    private final LruCache<String,Thumb> thumbCache=new LruCache<String,Thumb>(24*1024*1024){@Override protected int sizeOf(String k,Thumb t){return t.bmp==null?1:t.bmp.getByteCount();}};
    private final ExecutorService pool=Executors.newFixedThreadPool(3);
    private final Handler ui=new Handler(Looper.getMainLooper());

    private FrameLayout makeThumb(MediaItem it) {
        boolean vid=isVideo(it.name);
        FrameLayout fl=new FrameLayout(this); fl.setBackground(round(FIELD,10));
        ImageView iv=new ImageView(this); iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
        iv.setOutlineProvider(new ViewOutlineProvider(){@Override public void getOutline(View v,Outline o){o.setRoundRect(0,0,v.getWidth(),v.getHeight(),dp(10));}});
        iv.setClipToOutline(true);
        fl.addView(iv,new FrameLayout.LayoutParams(-1,-1));
        TextView dur=vid?new TextView(this):null;
        if(vid) {
            TextView play=new TextView(this); play.setText("▶"); play.setTextColor(0xFFFFFFFF); play.setTextSize(15); play.setGravity(Gravity.CENTER);
            GradientDrawable g=new GradientDrawable(); g.setShape(GradientDrawable.OVAL); g.setColor(0x99000000); play.setBackground(g);
            fl.addView(play,new FrameLayout.LayoutParams(dp(32),dp(32),Gravity.CENTER));
            dur.setTextColor(0xFFFFFFFF); dur.setTextSize(10); dur.setPadding(dp(4),dp(1),dp(4),dp(1)); dur.setBackground(round(0x99000000,4)); dur.setVisibility(View.GONE);
            FrameLayout.LayoutParams dlp=new FrameLayout.LayoutParams(-2,-2,Gravity.BOTTOM|Gravity.END); dlp.setMargins(0,0,dp(4),dp(4)); fl.addView(dur,dlp);
        }
        loadThumb(it,iv,dur);
        return fl;
    }
    private void loadThumb(MediaItem it,ImageView iv,TextView dur) {
        iv.setTag(it.path);
        Thumb c=thumbCache.get(it.path);
        if(c!=null){applyThumb(c,iv,dur);return;}
        pool.execute(()->{
            Thumb t=makeThumbData(it);
            thumbCache.put(it.path,t);
            ui.post(()->{ if(it.path.equals(iv.getTag())) applyThumb(t,iv,dur); });
        });
    }
    private void applyThumb(Thumb t,ImageView iv,TextView dur) {
        if(t.bmp!=null) iv.setImageBitmap(t.bmp);
        if(dur!=null && t.dur>0){dur.setText(fmtDur(t.dur)); dur.setVisibility(View.VISIBLE);}
    }
    private Thumb makeThumbData(MediaItem it) {
        Thumb t=new Thumb();
        try {
            if(isVideo(it.name)) {
                MediaMetadataRetriever r=new MediaMetadataRetriever();
                try {
                    r.setDataSource(it.path);
                    String d=r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
                    if(d!=null) t.dur=Long.parseLong(d);
                    Bitmap f=r.getFrameAtTime(0,MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
                    if(f==null) f=r.getFrameAtTime(1000000,MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
                    t.bmp=scaleDown(f,480);
                } finally { try{r.release();}catch(Exception ignored){} }
            } else t.bmp=decodeSampled(it.path,480);
        } catch(Throwable ignored){}
        return t;
    }
    private Bitmap scaleDown(Bitmap b,int max) {
        if(b==null)return null; int w=b.getWidth(),h=b.getHeight(),m=Math.max(w,h); if(m<=max)return b;
        float k=(float)max/m; return Bitmap.createScaledBitmap(b,Math.max(1,Math.round(w*k)),Math.max(1,Math.round(h*k)),true);
    }
    private Bitmap decodeSampled(String path,int max) {
        BitmapFactory.Options o=new BitmapFactory.Options(); o.inJustDecodeBounds=true; BitmapFactory.decodeFile(path,o);
        if(o.outWidth<=0||o.outHeight<=0)return null;
        int sample=1; while(o.outWidth/(sample*2)>=max && o.outHeight/(sample*2)>=max) sample*=2;
        BitmapFactory.Options o2=new BitmapFactory.Options(); o2.inSampleSize=sample;
        Bitmap b=BitmapFactory.decodeFile(path,o2); if(b==null)return null;
        try {
            int ori=new android.media.ExifInterface(path).getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION,1);
            int deg=ori==6?90:ori==3?180:ori==8?270:0;
            if(deg!=0){Matrix m=new Matrix(); m.postRotate(deg); b=Bitmap.createBitmap(b,0,0,b.getWidth(),b.getHeight(),m,true);}
        } catch(Exception ignored){}
        return b;
    }
    private String fmtDur(long ms) {
        long s=ms/1000,h=s/3600,m=(s%3600)/60,sec=s%60;
        return h>0?String.format(Locale.ROOT,"%d:%02d:%02d",h,m,sec):String.format(Locale.ROOT,"%d:%02d",m,sec);
    }
    private String fmtSize(long b) {
        if(b<1024*1024) return String.format(Locale.ROOT,"%.0f КБ",b/1024.0);
        if(b<1024L*1024*1024) return String.format(Locale.ROOT,"%.1f МБ",b/1048576.0);
        return String.format(Locale.ROOT,"%.2f ГБ",b/1073741824.0);
    }
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_SHORT).show();}
}
