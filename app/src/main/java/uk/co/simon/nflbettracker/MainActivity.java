package uk.co.simon.nflbettracker;

import android.app.*;
import android.os.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.text.*;
import java.util.*;
import java.util.concurrent.*;

public class MainActivity extends Activity {
    private static final String[] ESPN={
        "https://site.api.espn.com/apis/site/v2/sports/football/nfl/scoreboard",
        "https://site.web.api.espn.com/apis/site/v2/sports/football/nfl/scoreboard"
    };
    private static final long INTERVAL=30000;
    private final String[] picks={"Tampa Bay Buccaneers","Buffalo Bills","Baltimore Ravens"};
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private LinearLayout legs; private TextView summary,updated,connection; private boolean running;

    @Override public void onCreate(Bundle b){super.onCreate(b);setContentView(ui());}
    @Override protected void onStart(){super.onStart();running=true;refresh();}
    @Override protected void onStop(){running=false;handler.removeCallbacksAndMessages(null);super.onStop();}
    @Override protected void onDestroy(){worker.shutdownNow();super.onDestroy();}

    private View ui(){
        ScrollView scroll=new ScrollView(this);scroll.setBackgroundColor(Color.rgb(7,17,13));
        LinearLayout root=column();root.setPadding(dp(20),dp(28),dp(20),dp(30));scroll.addView(root);
        root.addView(text("LIVE TICKET",12,Color.rgb(112,225,161),true));
        root.addView(text("NFL Bet Tracker",30,Color.WHITE,true),space(0,18));
        LinearLayout ticket=card();summary=text("Checking 3 legs…",20,Color.WHITE,true);ticket.addView(summary);
        updated=text("ESPN live scores",13,Color.rgb(160,177,168),false);ticket.addView(updated,space(0,18));
        legs=column();ticket.addView(legs);root.addView(ticket);
        connection=text("Refreshing every 30 seconds while open",12,Color.rgb(133,150,141),false);root.addView(connection,space(4,0));
        Button button=new Button(this);button.setText("REFRESH NOW");button.setTextColor(Color.rgb(7,17,13));button.setTextSize(13);button.setTypeface(Typeface.DEFAULT,Typeface.BOLD);button.setBackgroundColor(Color.rgb(112,225,161));button.setOnClickListener(v->refresh());root.addView(button,space(52,0));return scroll;
    }

    private void refresh(){
        connection.setText("Updating from ESPN…");
        worker.execute(()->{String error="No ESPN endpoint responded";boolean ok=false;
            for(String endpoint:ESPN){try{JSONObject data=download(endpoint);ok=true;runOnUiThread(()->render(data));break;}catch(Exception e){error=e.getClass().getSimpleName()+": "+String.valueOf(e.getMessage());}}
            if(!ok){final String shown=error;runOnUiThread(()->connection.setText("ESPN update failed\n"+shown+"\nTap REFRESH NOW to retry."));}
            if(running)handler.postDelayed(this::refresh,INTERVAL);
        });
    }

    private JSONObject download(String endpoint)throws Exception{
        HttpURLConnection c=(HttpURLConnection)new URL(endpoint).openConnection();
        c.setInstanceFollowRedirects(true);c.setConnectTimeout(15000);c.setReadTimeout(15000);c.setUseCaches(false);
        c.setRequestProperty("User-Agent","Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 Chrome/140 Mobile Safari/537.36");
        c.setRequestProperty("Accept","application/json,text/plain,*/*");c.setRequestProperty("Accept-Encoding","identity");
        c.setRequestProperty("Referer","https://www.espn.com/");
        int code=c.getResponseCode();InputStream stream=code>=200&&code<300?c.getInputStream():c.getErrorStream();
        String body="";if(stream!=null)try(InputStream in=stream){body=new String(in.readAllBytes(),StandardCharsets.UTF_8);}
        c.disconnect();if(code<200||code>=300)throw new IOException("HTTP "+code+(body.isEmpty()?"":" — "+body.substring(0,Math.min(80,body.length()))));
        return new JSONObject(body);
    }

    private void render(JSONObject data){
        legs.removeAllViews();int live=0,won=0,lost=0;JSONArray events=data.optJSONArray("events");
        for(String pick:picks){Game g=find(events,pick);String state="UPCOMING",detail="Game not listed in the current ESPN window";int colour=Color.rgb(159,174,166);
            if(g!=null){detail=g.away+"  "+g.awayScore+"  –  "+g.homeScore+"  "+g.home+"\n"+g.detail;if(g.complete){if(g.pickScore>g.otherScore){state="WON";colour=Color.rgb(112,225,161);won++;}else{state="LOST";colour=Color.rgb(255,104,104);lost++;}}else if(g.started){live++;if(g.pickScore>g.otherScore){state="WINNING";colour=Color.rgb(112,225,161);}else if(g.pickScore<g.otherScore){state="TRAILING";colour=Color.rgb(255,190,92);}else{state="TIED";colour=Color.rgb(255,190,92);}}}
            LinearLayout row=card();row.setPadding(dp(16),dp(15),dp(16),dp(15));LinearLayout head=new LinearLayout(this);TextView name=text(pick,16,Color.WHITE,true);head.addView(name,new LinearLayout.LayoutParams(0,-2,1));head.addView(text(state,11,colour,true));row.addView(head);row.addView(text(detail,14,Color.rgb(190,205,197),false),space(8,0));legs.addView(row,space(0,10));}
        summary.setText(lost>0?"Ticket lost":won==3?"3/3 won":(won+live)+"/3 legs alive");updated.setText("Updated "+new SimpleDateFormat("HH:mm:ss",Locale.UK).format(new Date())+" • ESPN");connection.setText("Auto-refreshing every 30 seconds while open");
    }

    private Game find(JSONArray events,String pick){if(events==null)return null;for(int i=0;i<events.length();i++)try{JSONObject e=events.getJSONObject(i),c=e.getJSONArray("competitions").getJSONObject(0);JSONArray ts=c.getJSONArray("competitors");JSONObject selected=null,other=null;for(int j=0;j<ts.length();j++){JSONObject t=ts.getJSONObject(j);if(pick.equals(t.getJSONObject("team").optString("displayName")))selected=t;else other=t;}if(selected!=null&&other!=null){JSONObject status=e.getJSONObject("status"),type=status.getJSONObject("type");return new Game(selected,other,type.optBoolean("completed"),!"pre".equals(type.optString("state")),type.optString("shortDetail"));}}catch(Exception ignored){}return null;}
    private static class Game{String home,away,detail;int homeScore,awayScore,pickScore,otherScore;boolean complete,started;Game(JSONObject selected,JSONObject other,boolean complete,boolean started,String detail)throws Exception{this.complete=complete;this.started=started;this.detail=detail;pickScore=Integer.parseInt(selected.optString("score","0"));otherScore=Integer.parseInt(other.optString("score","0"));JSONObject h="home".equals(selected.optString("homeAway"))?selected:other,a=h==selected?other:selected;home=h.getJSONObject("team").optString("abbreviation");away=a.getJSONObject("team").optString("abbreviation");homeScore=Integer.parseInt(h.optString("score","0"));awayScore=Integer.parseInt(a.optString("score","0"));}}
    private LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    private LinearLayout card(){LinearLayout l=column();l.setPadding(dp(18),dp(18),dp(18),dp(18));l.setBackgroundColor(Color.rgb(17,35,27));return l;}
    private TextView text(String s,int size,int colour,boolean bold){TextView v=new TextView(this);v.setText(s);v.setTextSize(size);v.setTextColor(colour);v.setLineSpacing(0,1.15f);if(bold)v.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return v;}
    private LinearLayout.LayoutParams space(int top,int bottom){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(top);p.bottomMargin=dp(bottom);return p;}
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
}
