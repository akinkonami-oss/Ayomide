package com.ayomide.translator;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.view.Gravity;
import android.view.inputmethod.InputMethodManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONObject;
import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.net.URL;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final int SPEECH_REQUEST = 42;
    private EditText input;
    private TextView output, status, historyView;
    private Spinner sourceSpinner, targetSpinner;
    private Button translateButton;
    private TextToSpeech tts;
    private boolean ttsReady = false;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private SharedPreferences prefs;
    private final ArrayList<String> history = new ArrayList<>();

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences("ayomide", MODE_PRIVATE);
        buildUi();
        loadHistory();
        tts = new TextToSpeech(this, result -> { if (result == TextToSpeech.SUCCESS) ttsReady = true; });
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(20), dp(24), dp(20), dp(28));
        page.setBackgroundColor(Color.rgb(246, 248, 252));
        scroll.addView(page);
        TextView brand = new TextView(this);
        brand.setText("Ayomide"); brand.setTextSize(30); brand.setTypeface(null, Typeface.BOLD);
        brand.setTextColor(Color.rgb(23, 37, 84)); page.addView(brand);
        TextView subtitle = new TextView(this);
        subtitle.setText("English  ↔  Français"); subtitle.setTextSize(16);
        subtitle.setTextColor(Color.rgb(91, 103, 129)); subtitle.setPadding(0, dp(3), 0, dp(18)); page.addView(subtitle);
        LinearLayout languages = new LinearLayout(this); languages.setOrientation(LinearLayout.HORIZONTAL);
        sourceSpinner = new Spinner(this); targetSpinner = new Spinner(this);
        String[] langs = {"English", "Français"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, langs);
        sourceSpinner.setAdapter(adapter); targetSpinner.setAdapter(adapter); targetSpinner.setSelection(1);
        languages.addView(sourceSpinner, new LinearLayout.LayoutParams(0, dp(48), 1));
        Button swap = new Button(this); swap.setText("⇄"); swap.setOnClickListener(v -> swapLanguages());
        languages.addView(swap, new LinearLayout.LayoutParams(dp(64), dp(48)));
        languages.addView(targetSpinner, new LinearLayout.LayoutParams(0, dp(48), 1)); page.addView(languages);
        input = new EditText(this); input.setHint("Type something to translate…"); input.setTextSize(17);
        input.setGravity(Gravity.TOP | Gravity.START); input.setMinLines(4); input.setPadding(dp(14), dp(14), dp(14), dp(14));
        input.setBackgroundColor(Color.WHITE); input.setTextColor(Color.rgb(30, 41, 59));
        LinearLayout.LayoutParams inputLp = new LinearLayout.LayoutParams(-1, dp(140)); inputLp.topMargin = dp(14); page.addView(input, inputLp);
        LinearLayout row = new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL);
        Button voice = new Button(this); voice.setText("🎙 Voice"); voice.setOnClickListener(v -> startVoiceInput());
        translateButton = new Button(this); translateButton.setText("Translate"); translateButton.setTextColor(Color.WHITE);
        translateButton.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(49, 87, 213)));
        translateButton.setOnClickListener(v -> translate());
        row.addView(voice, new LinearLayout.LayoutParams(0, dp(52), 1));
        row.addView(translateButton, new LinearLayout.LayoutParams(0, dp(52), 1)); page.addView(row);
        status = new TextView(this); status.setText("Online translation • Internet required"); status.setTextSize(12);
        status.setTextColor(Color.rgb(100, 116, 139)); status.setPadding(0, dp(8), 0, dp(8)); page.addView(status);
        TextView resultLabel = new TextView(this); resultLabel.setText("TRANSLATION"); resultLabel.setTextSize(12);
        resultLabel.setTypeface(null, Typeface.BOLD); resultLabel.setTextColor(Color.rgb(91, 103, 129));
        resultLabel.setPadding(0, dp(10), 0, dp(6)); page.addView(resultLabel);
        output = new TextView(this); output.setText("Your translation will appear here."); output.setTextSize(18);
        output.setTextColor(Color.rgb(30, 41, 59)); output.setPadding(dp(14), dp(16), dp(14), dp(16));
        output.setBackgroundColor(Color.WHITE); output.setMinHeight(dp(100)); page.addView(output);
        LinearLayout actions = new LinearLayout(this); actions.setOrientation(LinearLayout.HORIZONTAL);
        Button copy = new Button(this); copy.setText("Copy"); copy.setOnClickListener(v -> copyTranslation());
        Button listen = new Button(this); listen.setText("▶ Listen"); listen.setOnClickListener(v -> speakTranslation());
        Button share = new Button(this); share.setText("Share"); share.setOnClickListener(v -> shareTranslation());
        actions.addView(copy, new LinearLayout.LayoutParams(0, dp(48), 1));
        actions.addView(listen, new LinearLayout.LayoutParams(0, dp(48), 1));
        actions.addView(share, new LinearLayout.LayoutParams(0, dp(48), 1)); page.addView(actions);
        TextView historyTitle = new TextView(this); historyTitle.setText("Recent translations"); historyTitle.setTextSize(18);
        historyTitle.setTypeface(null, Typeface.BOLD); historyTitle.setTextColor(Color.rgb(23, 37, 84));
        historyTitle.setPadding(0, dp(20), 0, dp(8)); page.addView(historyTitle);
        historyView = new TextView(this); historyView.setTextSize(14); historyView.setTextColor(Color.rgb(71, 85, 105));
        historyView.setPadding(dp(12), dp(12), dp(12), dp(12)); historyView.setBackgroundColor(Color.WHITE); page.addView(historyView);
        TextView note = new TextView(this); note.setText("Translations are provided online by MyMemory. Review important or formal translations before using them.");
        note.setTextSize(12); note.setTextColor(Color.rgb(100, 116, 139)); note.setPadding(0, dp(14), 0, 0); page.addView(note);
        setContentView(scroll);
    }

    private void translate() {
        String text = input.getText().toString().trim();
        if (text.isEmpty()) { input.setError("Enter text to translate"); return; }
        String from = sourceSpinner.getSelectedItemPosition() == 0 ? "en" : "fr";
        String to = targetSpinner.getSelectedItemPosition() == 0 ? "en" : "fr";
        if (from.equals(to)) { Toast.makeText(this, "Choose two different languages", Toast.LENGTH_SHORT).show(); return; }
        translateButton.setEnabled(false); status.setText("Translating…");
        ((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(input.getWindowToken(), 0);
        executor.execute(() -> {
            String result;
            try {
                String query = URLEncoder.encode(text, "UTF-8");
                URL url = new URL("https://api.mymemory.translated.net/get?q=" + query + "&langpair=" + from + "%7C" + to);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET"); conn.setConnectTimeout(12000); conn.setReadTimeout(15000);
                conn.setRequestProperty("Accept", "application/json");
                int code = conn.getResponseCode();
                BufferedReader reader = new BufferedReader(new InputStreamReader(code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream(), "UTF-8"));
                StringBuilder body = new StringBuilder(); String line;
                while ((line = reader.readLine()) != null) body.append(line);
                reader.close(); conn.disconnect();
                JSONObject json = new JSONObject(body.toString());
                JSONObject data = json.optJSONObject("responseData");
                result = data == null ? null : data.optString("translatedText", null);
                if (result == null || result.trim().isEmpty() || "null".equals(result)) throw new Exception("No translation returned. Try again later.");
                result = android.text.Html.fromHtml(result, android.text.Html.FROM_HTML_MODE_LEGACY).toString();
            } catch (Exception e) { result = ""; final String error = e.getMessage() == null ? "Check your internet connection and try again." : e.getMessage(); runOnUiThread(() -> { status.setText("Translation failed"); output.setText(error); translateButton.setEnabled(true); }); return; }
            final String translated = result;
            runOnUiThread(() -> {
                output.setText(translated); status.setText("Translation complete"); translateButton.setEnabled(true);
                addHistory((sourceSpinner.getSelectedItemPosition() == 0 ? "EN → FR" : "FR → EN") + "\n" + text + "\n→ " + translated);
            });
        });
    }
    private void swapLanguages() {
        int from = sourceSpinner.getSelectedItemPosition(); int to = targetSpinner.getSelectedItemPosition();
        sourceSpinner.setSelection(to); targetSpinner.setSelection(from);
        String oldInput = input.getText().toString(); String oldOutput = output.getText().toString();
        if (!oldOutput.equals("Your translation will appear here.") && !oldOutput.isEmpty()) { input.setText(oldOutput); output.setText(oldInput); }
    }
    private void startVoiceInput() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, sourceSpinner.getSelectedItemPosition() == 0 ? "en-US" : "fr-FR");
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, sourceSpinner.getSelectedItemPosition() == 0 ? "Speak in English" : "Parlez en français");
        try { startActivityForResult(intent, SPEECH_REQUEST); }
        catch (Exception e) { Toast.makeText(this, "Speech recognition is not available on this device.", Toast.LENGTH_LONG).show(); }
    }
    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == SPEECH_REQUEST && resultCode == RESULT_OK && data != null) {
            ArrayList<String> matches = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (matches != null && !matches.isEmpty()) { input.setText(matches.get(0)); input.setSelection(input.length()); }
        }
    }
    private void speakTranslation() {
        String text = output.getText().toString();
        if (text.isEmpty() || text.equals("Your translation will appear here.")) { Toast.makeText(this, "Translate something first", Toast.LENGTH_SHORT).show(); return; }
        if (!ttsReady || tts == null) { Toast.makeText(this, "Text-to-speech is not ready", Toast.LENGTH_SHORT).show(); return; }
        Locale locale = targetSpinner.getSelectedItemPosition() == 0 ? Locale.US : Locale.FRENCH;
        int result = tts.setLanguage(locale);
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            Toast.makeText(this, "Install the language voice in your phone's Text-to-Speech settings", Toast.LENGTH_LONG).show(); return;
        }
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "ayomide-translation");
    }
    private void copyTranslation() {
        String text = output.getText().toString();
        if (text.isEmpty() || text.equals("Your translation will appear here.")) { Toast.makeText(this, "Nothing to copy yet", Toast.LENGTH_SHORT).show(); return; }
        ClipboardManager clipboard = (ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(ClipData.newPlainText("Ayomide translation", text));
        Toast.makeText(this, "Translation copied", Toast.LENGTH_SHORT).show();
    }
    private void shareTranslation() {
        String text = output.getText().toString();
        if (text.isEmpty() || text.equals("Your translation will appear here.")) { Toast.makeText(this, "Nothing to share yet", Toast.LENGTH_SHORT).show(); return; }
        Intent send = new Intent(Intent.ACTION_SEND); send.setType("text/plain"); send.putExtra(Intent.EXTRA_TEXT, text);
        startActivity(Intent.createChooser(send, "Share translation"));
    }
    private void loadHistory() {
        String saved = prefs.getString("history", "");
        if (saved != null && !saved.isEmpty()) { for (String item : saved.split("\\n---\\n")) if (!item.trim().isEmpty()) history.add(item); }
        refreshHistory();
    }
    private void addHistory(String item) {
        history.add(0, item); while (history.size() > 10) history.remove(history.size() - 1);
        StringBuilder saved = new StringBuilder();
        for (String h : history) { if (saved.length() > 0) saved.append("\n---\n"); saved.append(h); }
        prefs.edit().putString("history", saved.toString()).apply(); refreshHistory();
    }
    private void refreshHistory() {
        if (historyView == null) return;
        if (history.isEmpty()) { historyView.setText("Your recent translations will appear here."); return; }
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < history.size(); i++) { if (i > 0) b.append("\n\n──────────\n\n"); b.append(history.get(i)); }
        historyView.setText(b.toString());
    }
    private int dp(int value) { return (int)(value * getResources().getDisplayMetrics().density + 0.5f); }
    @Override protected void onDestroy() { if (tts != null) { tts.stop(); tts.shutdown(); } executor.shutdownNow(); super.onDestroy(); }
}