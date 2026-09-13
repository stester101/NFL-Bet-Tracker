package uk.co.simon.nflbettracker;

import android.app.*;
import android.os.*;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
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
  static final int BG=Color.rgb(6,13,11),CARD=Color.rgb(15,29,24),CARD2=Color.rgb(22,41,34),WHITE=Color.rgb(244,248,246),MUTED=Color.rgb(151,171,161),GREEN=Color.rgb(105,226,157),AMBER=Color.rgb(255,190,92),RED=Color.rgb(255,104,104),BLUE=Color.rgb(111,174,255);
  static final String[] URLS={"https://site.api.espn.com/apis/site/v2/sports/football/nfl/scoreboard","https://site.web.api.espn.com/apis/site/v2/sports/football/nfl/scoreboard"};
  static final String[] TEAMS={"Arizona Cardinals","Atlanta Falcons","Baltimore Ravens","Buffalo Bills","Carolina Panthers","Chicago Bears","Cincinnati Bengals","Cleveland Browns","Dallas Cowboys","Denver Broncos","Detroit Lions","Green Bay Packers","Houston Texans","Indianapolis Colts","Jacksonville Jaguars","Kansas City Chiefs","Las Vegas Raiders","Los Angeles Chargers","Los Angeles Rams","Miami Dolphins","Minnesota Vikings","New England Patriots","New Orleans Saints","New York Giants","New York Jets","Philadelphia Eagles","Pittsburgh Steelers","San Francisco 49ers","Seattle Seahawks","Tampa Bay Buccaneers","Tennessee Titans","Washington Commanders"};
  final Handler handler=new Handler(Looper.getMainLooper());
  final ExecutorService worker=Executors.newSingleThreadExecutor();
  final ArrayList<String> picks=new ArrayList<>();
  LinearLayout body,betTab,scoresTab;
  TextView sync,countdown;
  JSONObject latest;
  boolean betScreen=true,running;
  long nextRefresh;
  float stake=5,odds=13.31f;

  @Override public void onCreate(Bundle b){super.onCreate(b);load();setContentView(layout());}
  @Override protected void onStart(){super.onStart();running=true;refresh();handler.post(clock);}
  @Override protected void onStop(){running=false;handler.removeCallbacksAndMessages(null);super.onStop();}
  @Override protected void onDestroy(){worker.shutdownNow();super.onDestroy();}

  View layout(){
    LinearLayout root=col();root.setBackgroundColor(BG);
    LinearLayout header=col();header.setPadding(dp(20),dp(20),dp(20),dp(12));
    LinearLayout bar=row();bar.setGravity(Gravity.CENTER_VERTICAL);
    TextView logo=txt("HUDDLE",13,GREEN,true);logo.setLetterSpacing(.14f);bar.addView(logo,new LinearLayout.LayoutParams(0,dp(44),1));
    TextView reload=btn("↻",22,CARD2);reload.setContentDescription("Refresh now");reload.setOnClickListener(v->refresh());bar.addView(reload,new LinearLayout.LayoutParams(dp(44),dp(44)));header.addView(bar);
    LinearLayout tabs=row();tabs.setPadding(0,dp(10),0,0);betTab=tab("MY BET");scoresTab=tab("LIVE SCORES");tabs.addView(betTab,new LinearLayout.LayoutParams(0,dp(46),1));tabs.addView(scoresTab,new LinearLayout.LayoutParams(0,dp(46),1));
    betTab.setOnClickListener(v->show(true));scoresTab.setOnClickListener(v->show(false));header.addView(tabs);root.addView(header);
    ScrollView scroll=new ScrollView(this);body=col();body.setPadding(dp(20),dp(8),dp(20),dp(32));scroll.addView(body);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
    LinearLayout footer=row();footer.setGravity(Gravity.CENTER_VERTICAL);footer.setPadding(dp(20),dp(8),dp(20),dp(14));sync=txt("Connecting to ESPN",12,MUTED,false);countdown=txt("",12,MUTED,true);footer.addView(sync,new LinearLayout.LayoutParams(0,-2,1));footer.addView(countdown);root.addView(footer);
    styleTabs();render();return root;
  }

  void show(boolean bet){betScreen=bet;styleTabs();render();}
  LinearLayout tab(String title){LinearLayout x=row();x.setGravity(Gravity.CENTER);x.addView(txt(title,12,MUTED,true));return x;}
  void styleTabs(){betTab.setBackground(round(betScreen?GREEN:CARD,14));scoresTab.setBackground(round(betScreen?CARD:GREEN,14));((TextView)betTab.getChildAt(0)).setTextColor(betScreen?BG:MUTED);((TextView)scoresTab.getChildAt(0)).setTextColor(betScreen?MUTED:BG);}
  void render(){body.removeAllViews();if(betScreen)renderBet();else renderScores();}

  void renderBet(){
    JSONArray events=latest==null?null:latest.optJSONArray("events");ArrayList<Game> games=new ArrayList<>();int won=0,lost=0,live=0;
    for(String p:picks){Game g=find(events,p);games.add(g);if(g!=null){if(g.complete){if(g.pickScore>g.otherScore)won++;else lost++;}else if(g.started)live++;}}
    LinearLayout hero=panel(CARD);LinearLayout top=row();top.setGravity(Gravity.CENTER_VERTICAL);LinearLayout copy=col();
    String headline=lost>0?"Accumulator lost":won==picks.size()?picks.size()+"/"+picks.size()+" landed":(won+live)+"/"+picks.size()+" legs active";
    copy.addView(txt(headline,25,WHITE,true));copy.addView(txt(picks.size()+"-leg moneyline  •  £"+money(stake)+" stake",13,MUTED,false),space(4,0));top.addView(copy,new LinearLayout.LayoutParams(0,-2,1));
    TextView edit=btn("EDIT",12,CARD2);edit.setOnClickListener(v->editTicket());top.addView(edit,new LinearLayout.LayoutParams(dp(68),dp(40)));hero.addView(top);
    LinearLayout stats=row();stats.setPadding(0,dp(22),0,0);stats.addView(metric("ODDS",String.format(Locale.UK,"%.2f",odds)),weight());stats.addView(metric("RETURN","£"+money(stake*odds)),weight());stats.addView(metric("STATUS",lost>0?"LOST":won==picks.size()?"WON":"OPEN"),weight());hero.addView(stats);body.addView(hero);
    TextView label=txt("LEGS",12,MUTED,true);label.setLetterSpacing(.1f);body.addView(label,space(24,10));
    for(int i=0;i<picks.size();i++)body.addView(leg(picks.get(i),games.get(i),i),space(0,10));
    TextView add=btn("+  ADD OR REMOVE LEGS",13,CARD2);add.setTextColor(GREEN);add.setOnClickListener(v->chooseTeams());body.addView(add,new LinearLayout.LayoutParams(-1,dp(52)));
  }

  View leg(String pick,Game g,int index){
    LinearLayout card=panel(CARD);LinearLayout head=row();head.setGravity(Gravity.CENTER_VERTICAL);
    TextView n=txt(String.valueOf(index+1),12,BG,true);n.setGravity(Gravity.CENTER);n.setBackground(round(GREEN,99));head.addView(n,new LinearLayout.LayoutParams(dp(30),dp(30)));
    TextView name=txt(pick,16,WHITE,true);name.setPadding(dp(12),0,0,0);head.addView(name,new LinearLayout.LayoutParams(0,-2,1));
    String state="UPCOMING";int color=MUTED;if(g!=null){if(g.complete){state=g.pickScore>g.otherScore?"WON":"LOST";color=g.pickScore>g.otherScore?GREEN:RED;}else if(g.started){state=g.pickScore>g.otherScore?"WINNING":g.pickScore<g.otherScore?"TRAILING":"TIED";color=g.pickScore>g.otherScore?GREEN:AMBER;}}
    head.addView(txt(state,11,color,true));card.addView(head);
    if(g==null)card.addView(txt("Fixture not found in this gameweek",13,MUTED,false),space(14,0));
    else{LinearLayout scores=row();scores.setGravity(Gravity.CENTER);scores.addView(score(g.away,g.awayScore),weight());scores.addView(txt("—",18,MUTED,false),new LinearLayout.LayoutParams(dp(34),-2));scores.addView(score(g.home,g.homeScore),weight());card.addView(scores,space(16,0));TextView detail=txt(g.detail,12,g.started&&!g.complete?GREEN:MUTED,true);detail.setGravity(Gravity.CENTER);card.addView(detail,space(10,0));}
    card.setOnLongClickListener(v->{if(picks.size()>1){picks.remove(index);save();render();}return true;});return card;
  }

  void renderScores(){
    body.addView(txt("NFL Gameweek",28,WHITE,true));body.addView(txt("Every fixture, live score and final result",13,MUTED,false),space(4,18));
    JSONArray events=latest==null?null:latest.optJSONArray("events");if(events==null){body.addView(empty("Loading this gameweek…"));return;}
    int shown=0;for(int i=0;i<events.length();i++)try{Game g=fromEvent(events.getJSONObject(i),null);if(g!=null){body.addView(gameCard(g),space(0,10));shown++;}}catch(Exception ignored){}
    if(shown==0)body.addView(empty("No NFL fixtures returned for this gameweek."));
  }
  View gameCard(Game g){
    LinearLayout card=panel(CARD);LinearLayout line=row();String state=g.complete?"FINAL":g.started?"● LIVE":"UPCOMING";int c=g.complete?MUTED:g.started?GREEN:BLUE;line.addView(txt(state,11,c,true),weight());line.addView(txt(g.detail,11,MUTED,true));card.addView(line);
    card.addView(teamRow(g.awayName,g.away,g.awayScore,g.started),space(14,0));card.addView(teamRow(g.homeName,g.home,g.homeScore,g.started),space(10,0));return card;
  }
  LinearLayout teamRow(String name,String abbr,int points,boolean showScore){LinearLayout x=row();x.setGravity(Gravity.CENTER_VERTICAL);TextView badge=txt(abbr,11,GREEN,true);badge.setGravity(Gravity.CENTER);badge.setBackground(round(CARD2,10));x.addView(badge,new LinearLayout.LayoutParams(dp(48),dp(38)));TextView team=txt(name,15,WHITE,true);team.setPadding(dp(12),0,0,0);x.addView(team,weight());x.addView(txt(showScore?String.valueOf(points):"—",23,WHITE,true));return x;}

  void refresh(){sync.setText("Updating ESPN scores…");worker.execute(()->{String error="No response";for(String url:URLS)try{latest=download(url);nextRefresh=System.currentTimeMillis()+30000;runOnUiThread(()->{sync.setText("Updated "+new SimpleDateFormat("HH:mm:ss",Locale.UK).format(new Date()));render();});return;}catch(Exception e){error=e.getClass().getSimpleName()+": "+e.getMessage();}String message=error;runOnUiThread(()->sync.setText("Update failed  •  "+message));});}
  JSONObject download(String url)throws Exception{HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();c.setInstanceFollowRedirects(true);c.setConnectTimeout(15000);c.setReadTimeout(15000);c.setUseCaches(false);c.setRequestProperty("User-Agent","Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 Chrome/140 Mobile Safari/537.36");c.setRequestProperty("Accept","application/json,text/plain,*/*");c.setRequestProperty("Accept-Encoding","identity");c.setRequestProperty("Referer","https://www.espn.com/");int code=c.getResponseCode();InputStream stream=code>=200&&code<300?c.getInputStream():c.getErrorStream();String body="";if(stream!=null)try(InputStream in=stream){body=new String(in.readAllBytes(),StandardCharsets.UTF_8);}c.disconnect();if(code<200||code>=300)throw new IOException("HTTP "+code);return new JSONObject(body);}
  final Runnable clock=new Runnable(){public void run(){if(!running)return;long s=Math.max(0,(nextRefresh-System.currentTimeMillis()+999)/1000);countdown.setText(nextRefresh==0?"":s>0?"Refresh in "+s+"s":"Refreshing…");if(nextRefresh>0&&s==0){nextRefresh=Long.MAX_VALUE;refresh();}handler.postDelayed(this,1000);}};

  Game find(JSONArray events,String pick){if(events==null)return null;for(int i=0;i<events.length();i++)try{Game g=fromEvent(events.getJSONObject(i),pick);if(g!=null)return g;}catch(Exception ignored){}return null;}
  Game fromEvent(JSONObject e,String selectedName)throws Exception{JSONArray teams=e.getJSONArray("competitions").getJSONObject(0).getJSONArray("competitors");JSONObject home=null,away=null,selected=null;for(int i=0;i<teams.length();i++){JSONObject t=teams.getJSONObject(i);if("home".equals(t.optString("homeAway")))home=t;else away=t;if(selectedName!=null&&selectedName.equals(t.getJSONObject("team").optString("displayName")))selected=t;}if(home==null||away==null||selectedName!=null&&selected==null)return null;JSONObject other=selected==null?away:selected==home?away:home;if(selected==null)selected=home;JSONObject type=e.getJSONObject("status").getJSONObject("type");return new Game(home,away,selected,other,type.optBoolean("completed"),!"pre".equals(type.optString("state")),type.optString("shortDetail"));}
  static class Game{String home,away,homeName,awayName,detail;int homeScore,awayScore,pickScore,otherScore;boolean complete,started;Game(JSONObject h,JSONObject a,JSONObject pick,JSONObject other,boolean done,boolean begun,String d)throws Exception{complete=done;started=begun;detail=d;home=h.getJSONObject("team").optString("abbreviation");away=a.getJSONObject("team").optString("abbreviation");homeName=h.getJSONObject("team").optString("shortDisplayName");awayName=a.getJSONObject("team").optString("shortDisplayName");homeScore=points(h);awayScore=points(a);pickScore=points(pick);otherScore=points(other);}static int points(JSONObject t){try{return Integer.parseInt(t.optString("score","0"));}catch(Exception e){return 0;}}}

  void chooseTeams(){boolean[] checked=new boolean[TEAMS.length];for(int i=0;i<TEAMS.length;i++)checked[i]=picks.contains(TEAMS[i]);new AlertDialog.Builder(this).setTitle("Select moneyline legs").setMultiChoiceItems(TEAMS,checked,(d,i,on)->checked[i]=on).setNegativeButton("Cancel",null).setPositiveButton("Save",(d,w)->{picks.clear();for(int i=0;i<TEAMS.length;i++)if(checked[i])picks.add(TEAMS[i]);if(picks.isEmpty())picks.add("Buffalo Bills");save();render();}).show();}
  void editTicket(){LinearLayout box=col();box.setPadding(dp(24),0,dp(24),0);EditText s=new EditText(this);s.setHint("Stake");s.setInputType(8194);s.setText(money(stake));EditText o=new EditText(this);o.setHint("Decimal odds");o.setInputType(8194);o.setText(String.format(Locale.UK,"%.2f",odds));box.addView(s);box.addView(o);new AlertDialog.Builder(this).setTitle("Ticket details").setView(box).setNegativeButton("Cancel",null).setPositiveButton("Save",(d,w)->{try{stake=Float.parseFloat(s.getText().toString());odds=Float.parseFloat(o.getText().toString());save();render();}catch(Exception ignored){}}).show();}
  void load(){try{JSONObject o=new JSONObject(getPreferences(0).getString("ticket",""));JSONArray a=o.getJSONArray("picks");for(int i=0;i<a.length();i++)picks.add(a.getString(i));stake=(float)o.optDouble("stake",5);odds=(float)o.optDouble("odds",13.31);}catch(Exception e){picks.add("Tampa Bay Buccaneers");picks.add("Buffalo Bills");picks.add("Baltimore Ravens");}}
  void save(){try{JSONObject o=new JSONObject();o.put("picks",new JSONArray(picks));o.put("stake",stake);o.put("odds",odds);getPreferences(0).edit().putString("ticket",o.toString()).apply();}catch(Exception ignored){}}

  LinearLayout metric(String label,String value){LinearLayout x=col();x.addView(txt(label,10,MUTED,true));x.addView(txt(value,18,WHITE,true),space(5,0));return x;}
  LinearLayout score(String team,int points){LinearLayout x=col();TextView t=txt(team,13,MUTED,true);t.setGravity(Gravity.CENTER);TextView p=txt(String.valueOf(points),27,WHITE,true);p.setGravity(Gravity.CENTER);x.addView(t);x.addView(p,space(3,0));return x;}
  TextView empty(String s){TextView x=txt(s,14,MUTED,false);x.setGravity(Gravity.CENTER);x.setPadding(dp(20),dp(48),dp(20),dp(48));x.setBackground(round(CARD,18));return x;}
  LinearLayout panel(int color){LinearLayout x=col();x.setPadding(dp(18),dp(18),dp(18),dp(18));x.setBackground(round(color,18));return x;}
  TextView btn(String s,int size,int color){TextView x=txt(s,size,WHITE,true);x.setGravity(Gravity.CENTER);x.setBackground(round(color,14));return x;}
  GradientDrawable round(int color,int radius){GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(dp(radius));return g;}
  LinearLayout col(){LinearLayout x=new LinearLayout(this);x.setOrientation(LinearLayout.VERTICAL);return x;}
  LinearLayout row(){return new LinearLayout(this);}
  TextView txt(String s,int size,int color,boolean bold){TextView x=new TextView(this);x.setText(s);x.setTextSize(size);x.setTextColor(color);x.setLineSpacing(0,1.12f);if(bold)x.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return x;}
  LinearLayout.LayoutParams weight(){return new LinearLayout.LayoutParams(0,-2,1);}
  LinearLayout.LayoutParams space(int top,int bottom){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(top);p.bottomMargin=dp(bottom);return p;}
  String money(float n){return String.format(Locale.UK,"%.2f",n);}
  int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
}
