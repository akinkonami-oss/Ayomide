package com.ayomide.translator;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.text.Html;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
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
    private final LinkedHashMap<String, String> languages = new LinkedHashMap<>();
    private boolean translating = false;
    private String latestTranslation = "";

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences("ayomide", MODE_PRIVATE);
        addLanguages();
        loadHistory();
        buildUi();
        tts = new TextToSpeech(this, result -> { if (result == TextToSpeech.SUCCESS) ttsReady = true; });
    }

    private void addLanguages() {
        languages.put("English", "en"); languages.put("Français", "fr");
        languages.put("Español", "es"); languages.put("Português", "pt");
        languages.put("Deutsch", "de"); languages.put("Italiano", "it");
        languages.put("中文 (Chinese)", "zh"); languages.put("日本語 (Japanese)", "ja");
        languages.put("한국어 (Korean)", "ko"); languages.put("العربية (Arabic)", "ar");
        languages.put("हिन्दी (Hindi)", "hi"); languages.put("اردو (Urdu)", "ur");
        languages.put("বাংলা (Bengali)", "bn"); languages.put("Русский (Russian)", "ru");
        languages.put("Türkçe (Turkish)", "tr"); languages.put("Nederlands (Dutch)", "nl");
        languages.put("Polski (Polish)", "pl"); languages.put("Українська (Ukrainian)", "uk");
        languages.put("ไทย (Thai)", "th"); languages.put("Tiếng Việt (Vietnamese)", "vi");
        languages.put("Bahasa Indonesia", "id"); languages.put("Bahasa Melayu", "ms");
        languages.put("Filipino", "tl"); languages.put("Svenska (Swedish)", "sv");
        languages.put("Ελληνικά (Greek)", "el"); languages.put("עברית (Hebrew)", "he");
        languages.put("فارسی (Persian)", "fa"); languages.put("Română (Romanian)", "ro");
        languages.put("Magyar (Hungarian)", "hu"); languages.put("Čeština (Czech)", "cs");
        languages.put("Dansk (Danish)", "da"); languages.put("Suomi (Finnish)", "fi");
        languages.put("Norsk (Norwegian)", "no"); languages.put("Hrvatski (Croatian)", "hr");
        languages.put("Српски (Serbian)", "sr"); languages.put("Български (Bulgarian)", "bg");
        languages.put("Kiswahili (Swahili)", "sw"); languages.put("Afrikaans", "af");
        languages.put("Yorùbá (Yoruba)", "yo"); languages.put("Hausa", "ha");
        languages.put("Igbo", "ig"); languages.put("Amharic", "am");
        languages.put("Català (Catalan)", "ca"); languages.put("বাংলা (Bengali)", "bn");
    }

    private boolean dark() { return prefs.getString("theme", "light").equals("dark"); }
    private int bg() { return dark() ? Color.rgb(18, 17, 29) : Color.rgb(248, 247, 255); }
    private int panel() { return dark() ? Color.rgb(34, 31, 49) : Color.WHITE; }
    private int ink() { return dark() ? Color.rgb(245, 241, 255) : Color.rgb(42, 34, 67); }
    private int muted() { return dark() ? Color.rgb(190, 181, 211) : Color.rgb(113, 102, 143); }
    private int borderColor() { return dark() ? Color.rgb(68, 59, 91) : Color.rgb(230, 225, 245); }
    private int purple() { return Color.rgb(112, 74, 220); }
    private int green() { return Color.rgb(40, 185, 118); }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(16), dp(16), dp(16), dp(28));
        page.setBackgroundColor(bg());
        scroll.addView(page);

        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setPadding(dp(20), dp(18), dp(20), dp(18));
        hero.setBackground(gradient(dark() ? Color.rgb(56, 37, 112) : Color.rgb(91, 55, 190),
                dark() ? Color.rgb(30, 101, 78) : Color.rgb(37, 155, 112), dp(24)));
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView brand = text("Ayomide", 30, Color.WHITE, true);
        top.addView(brand, new LinearLayout.LayoutParams(0, -2, 1));
        Button settings = button("⚙ Settings", Color.rgb(236, 231, 255), Color.rgb(72, 43, 145));
        settings.setOnClickListener(v -> showSettings());
        top.addView(settings, new LinearLayout.LayoutParams(-2, dp(42)));
        hero.addView(top);
        TextView greeting = text("Your language. Your world.", 16, Color.rgb(244, 241, 255), false);
        LinearLayout.LayoutParams greetLp = new LinearLayout.LayoutParams(-1, -2);
        greetLp.topMargin = dp(4); hero.addView(greeting, greetLp);
        TextView tagline = text("Translate conversations, messages and longer text.", 12, Color.rgb(210, 255, 230), false);
        tagline.setPadding(0, dp(7), 0, 0); hero.addView(tagline);
        TextView profile = text("Welcome, " + prefs.getString("username", "Language explorer"), 12, Color.WHITE, true);
        profile.setPadding(0, dp(10), 0, 0); hero.addView(profile);
        LinearLayout.LayoutParams heroLp = new LinearLayout.LayoutParams(-1, -2);
        heroLp.bottomMargin = dp(16); page.addView(hero, heroLp);

        TextView section = text("TRANSLATE", 12, green(), true);
        section.setLetterSpacing(0.12f); page.addView(section);
        LinearLayout languagesRow = new LinearLayout(this);
        languagesRow.setOrientation(LinearLayout.HORIZONTAL);
        sourceSpinner = new Spinner(this); targetSpinner = new Spinner(this);
        ArrayList<String> names = new ArrayList<>(languages.keySet());
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, names);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        sourceSpinner.setAdapter(adapter); targetSpinner.setAdapter(adapter);
        sourceSpinner.setSelection(Math.max(0, names.indexOf(prefs.getString("from", "English"))));
        targetSpinner.setSelection(Math.max(0, names.indexOf(prefs.getString("to", "Français"))));
        languagesRow.addView(sourceSpinner, new LinearLayout.LayoutParams(0, dp(52), 1));
        Button swap = button("⇄", dark() ? Color.rgb(54, 47, 75) : Color.rgb(236, 231, 255), dark() ? Color.rgb(202, 184, 255) : Color.rgb(91, 55, 190));
        swap.setOnClickListener(v -> swapLanguages());
        LinearLayout.LayoutParams swapLp = new LinearLayout.LayoutParams(dp(54), dp(48));
        swapLp.setMargins(dp(5), 0, dp(5), 0); languagesRow.addView(swap, swapLp);
        languagesRow.addView(targetSpinner, new LinearLayout.LayoutParams(0, dp(52), 1));
        LinearLayout.LayoutParams langsLp = new LinearLayout.LayoutParams(-1, -2);
        langsLp.topMargin = dp(7); page.addView(languagesRow, langsLp);

        input = new EditText(this);
        input.setHint("Type or paste a sentence, conversation or longer text…");
        input.setTextSize(prefs.getInt("fontSize", 17));
        input.setGravity(Gravity.TOP | Gravity.START);
        input.setMinLines(5); input.setPadding(dp(15), dp(15), dp(15), dp(15));
        input.setBackground(rounded(panel(), dp(18), borderColor()));
        input.setTextColor(ink()); input.setHintTextColor(muted());
        input.setTypeface(chosenTypeface());
        LinearLayout.LayoutParams inputLp = new LinearLayout.LayoutParams(-1, dp(170));
        inputLp.topMargin = dp(12); page.addView(input, inputLp);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        Button voice = button("🎙 Voice input", dark() ? Color.rgb(34, 73, 59) : Color.rgb(225, 248, 235), dark() ? Color.rgb(143, 255, 201) : Color.rgb(25, 125, 78));
        voice.setOnClickListener(v -> startVoiceInput());
        translateButton = button("Translate  ↗", purple(), Color.WHITE);
        translateButton.setOnClickListener(v -> translate());
        row.addView(voice, new LinearLayout.LayoutParams(0, dp(52), 1));
        LinearLayout.LayoutParams trLp = new LinearLayout.LayoutParams(0, dp(52), 1);
        trLp.leftMargin = dp(8); row.addView(translateButton, trLp);
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(-1, -2);
        rowLp.topMargin = dp(10); page.addView(row, rowLp);

        status = text("● Online translator • Internet connection required", 12, muted(), false);
        status.setPadding(dp(2), dp(10), 0, dp(6)); page.addView(status);
        TextView resultLabel = text("YOUR TRANSLATION", 12, green(), true);
        resultLabel.setLetterSpacing(0.10f);
        LinearLayout.LayoutParams resultLabelLp = new LinearLayout.LayoutParams(-1, -2);
        resultLabelLp.topMargin = dp(8); page.addView(resultLabel, resultLabelLp);
        output = text("Your translation will appear here.", prefs.getInt("fontSize", 17), ink(), false);
        output.setTypeface(chosenTypeface());
        output.setPadding(dp(16), dp(18), dp(16), dp(18));
        output.setBackground(rounded(panel(), dp(18), borderColor()));
        output.setMinHeight(dp(120));
        LinearLayout.LayoutParams outputLp = new LinearLayout.LayoutParams(-1, -2);
        outputLp.topMargin = dp(8); page.addView(output, outputLp);

        LinearLayout actions = new LinearLayout(this); actions.setOrientation(LinearLayout.HORIZONTAL);
        Button copy = button("Copy", dark() ? Color.rgb(54, 47, 75) : Color.rgb(236, 231, 255), dark() ? Color.rgb(202, 184, 255) : Color.rgb(91, 55, 190));
        copy.setOnClickListener(v -> copyTranslation());
        Button listen = button("▶ Listen", dark() ? Color.rgb(34, 73, 59) : Color.rgb(225, 248, 235), dark() ? Color.rgb(143, 255, 201) : Color.rgb(25, 125, 78));
        listen.setOnClickListener(v -> speakTranslation());
        Button share = button("Share", dark() ? Color.rgb(54, 47, 75) : Color.rgb(236, 231, 255), dark() ? Color.rgb(202, 184, 255) : Color.rgb(91, 55, 190));
        share.setOnClickListener(v -> shareTranslation());
        actions.addView(copy, new LinearLayout.LayoutParams(0, dp(46), 1));
        LinearLayout.LayoutParams listenLp = new LinearLayout.LayoutParams(0, dp(46), 1);
        listenLp.leftMargin = dp(6); actions.addView(listen, listenLp);
        LinearLayout.LayoutParams shareLp = new LinearLayout.LayoutParams(0, dp(46), 1);
        shareLp.leftMargin = dp(6); actions.addView(share, shareLp);
        LinearLayout.LayoutParams actionsLp = new LinearLayout.LayoutParams(-1, -2);
        actionsLp.topMargin = dp(8); page.addView(actions, actionsLp);

        LinearLayout historyHead = new LinearLayout(this);
        historyHead.setGravity(Gravity.CENTER_VERTICAL);
        TextView historyTitle = text("Recent translations", 18, ink(), true);
        historyHead.addView(historyTitle, new LinearLayout.LayoutParams(0, -2, 1));
        Button clearHistory = button("Clear", dark() ? Color.rgb(54, 47, 75) : Color.rgb(236, 231, 255), dark() ? Color.rgb(202, 184, 255) : Color.rgb(91, 55, 190));
        clearHistory.setOnClickListener(v -> confirmClearHistory());
        historyHead.addView(clearHistory, new LinearLayout.LayoutParams(-2, dp(38)));
        LinearLayout.LayoutParams hhLp = new LinearLayout.LayoutParams(-1, -2);
        hhLp.topMargin = dp(20); page.addView(historyHead, hhLp);
        historyView = text("Your recent translations will appear here.", 14, ink(), false);
        historyView.setPadding(dp(14), dp(14), dp(14), dp(14));
        historyView.setBackground(rounded(panel(), dp(18), borderColor()));
        LinearLayout.LayoutParams hvLp = new LinearLayout.LayoutParams(-1, -2);
        hvLp.topMargin = dp(8); page.addView(historyView, hvLp);
        TextView note = text("Online services can have limits or temporary outages. For important documents, review the translation before relying on it.", 11, muted(), false);
        note.setPadding(dp(2), dp(12), dp(2), 0); page.addView(note);
        setContentView(scroll);
        refreshHistory();
    }

    private void showSettings() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL); box.setPadding(dp(20), dp(8), dp(20), dp(4));
        EditText username = new EditText(this); username.setSingleLine(true);
        username.setHint("Your display name"); username.setText(prefs.getString("username", ""));
        box.addView(label("Profile name")); box.addView(username);
        box.addView(label("Appearance"));
        Spinner theme = new Spinner(this);
        ArrayAdapter<String> themeAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, new String[]{"Light mode", "Dark mode"});
        theme.setAdapter(themeAdapter); theme.setSelection(dark() ? 1 : 0); box.addView(theme);
        box.addView(label("Font size"));
        Spinner size = new Spinner(this);
        ArrayAdapter<String> sizeAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, new String[]{"Small", "Medium", "Large", "Extra large"});
        size.setAdapter(sizeAdapter);
        int currentSize = prefs.getInt("fontSize", 17);
        size.setSelection(currentSize <= 14 ? 0 : currentSize <= 17 ? 1 : currentSize <= 20 ? 2 : 3);
        box.addView(size);
        box.addView(label("Font style"));
        Spinner font = new Spinner(this);
        ArrayAdapter<String> fontAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, new String[]{"Modern Sans", "Serif", "Monospace"});
        font.setAdapter(fontAdapter);
        String fontName = prefs.getString("fontStyle", "sans");
        font.setSelection(fontName.equals("serif") ? 1 : fontName.equals("mono") ? 2 : 0);
        box.addView(font);
        new AlertDialog.Builder(this).setTitle("Ayomide settings").setView(box)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save settings", (dialog, which) -> {
                    prefs.edit().putString("username", username.getText().toString().trim())
                            .putString("theme", theme.getSelectedItemPosition() == 1 ? "dark" : "light")
                            .putInt("fontSize", new int[]{14,17,20,23}[size.getSelectedItemPosition()])
                            .putString("fontStyle", new String[]{"sans","serif","mono"}[font.getSelectedItemPosition()]).apply();
                    String savedInput = input == null ? "" : input.getText().toString();
                    String savedOutput = output == null ? "" : output.getText().toString();
                    buildUi();
                    input.setText(savedInput);
                    output.setText(savedOutput);
                    Toast.makeText(this, "Settings saved", Toast.LENGTH_SHORT).show();
                }).show();
    }

    private TextView label(String value) {
        TextView t = text(value, 13, ink(), true);
        t.setPadding(0, dp(12), 0, dp(2)); return t;
    }

    private Typeface chosenTypeface() {
        String f = prefs.getString("fontStyle", "sans");
        if (f.equals("serif")) return Typeface.SERIF;
        if (f.equals("mono")) return Typeface.MONOSPACE;
        return Typeface.create("sans-serif", Typeface.NORMAL);
    }

    private void translate() {
        if (translating) return;
        final String sourceText = input.getText().toString().trim();
        if (sourceText.isEmpty()) { input.setError("Enter text to translate"); return; }
        final String fromName = sourceSpinner.getSelectedItem().toString();
        final String toName = targetSpinner.getSelectedItem().toString();
        final String from = languages.get(fromName), to = languages.get(toName);
        if (from.equals(to)) { Toast.makeText(this, "Choose two different languages", Toast.LENGTH_SHORT).show(); return; }
        translating = true; translateButton.setEnabled(false); status.setText("Connecting to translation services…");
        ((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(input.getWindowToken(), 0);
        prefs.edit().putString("from", fromName).putString("to", toName).apply();
        executor.execute(() -> {
            try {
                ArrayList<String> chunks = splitText(sourceText, 1600);
                StringBuilder translated = new StringBuilder();
                for (int i = 0; i < chunks.size(); i++) {
                    final int part = i + 1, total = chunks.size();
                    runOnUiThread(() -> status.setText(total > 1 ? "Translating section " + part + " of " + total + "…" : "Translating online…"));
                    String piece = translateChunk(chunks.get(i), from, to);
                    if (translated.length() > 0) translated.append(" ");
                    translated.append(piece.trim());
                }
                String result = translated.toString().trim();
                if (result.isEmpty()) throw new Exception("The translation service returned an empty result. Please try again.");
                runOnUiThread(() -> {
                    latestTranslation = result; output.setText(result); status.setText("Translation complete • " + chunks.size() + (chunks.size() == 1 ? " section" : " sections"));
                    addHistory(fromName + " → " + toName + "\n" + sourceText + "\n→ " + result);
                    translating = false; translateButton.setEnabled(true);
                });
            } catch (Exception e) {
                String message = e.getMessage() == null ? "" : e.getMessage();
                if (message.contains("Unable to resolve host") || message.contains("No address associated with hostname")) message = "No internet connection. Connect to Wi-Fi or mobile data and try again.";
                else if (message.contains("timed out") || message.contains("timeout")) message = "The translation service took too long to respond. Please retry.";
                else if (message.trim().isEmpty()) message = "Both online translation services are unavailable right now. Check your connection and try again.";
                final String error = message;
                runOnUiThread(() -> { status.setText("Translation unavailable"); output.setText(error); translating = false; translateButton.setEnabled(true); });
            }
        });
    }

    private String translateChunk(String text, String from, String to) throws Exception {
        Exception first = null;
        try { return googleTranslate(text, from, to); }
        catch (Exception e) { first = e; }
        try {
            if (text.length() <= 450) return myMemoryTranslate(text, from, to);
            ArrayList<String> backupParts = splitText(text, 400);
            StringBuilder combined = new StringBuilder();
            for (String part : backupParts) {
                if (combined.length() > 0) combined.append(" ");
                combined.append(myMemoryTranslate(part, from, to).trim());
            }
            return combined.toString();
        } catch (Exception e) {
            String detail = e.getMessage();
            if (detail == null || detail.trim().isEmpty()) detail = first == null ? "" : first.getMessage();
            throw new Exception("Online translation failed. Check your internet connection or try again in a moment." + (detail == null || detail.isEmpty() ? "" : "\nDetails: " + detail));
        }
    }

    private String googleTranslate(String text, String from, String to) throws Exception {
        String url = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=" + from + "&tl=" + to + "&dt=t&q=" + URLEncoder.encode(text, "UTF-8");
        String body = httpGet(url, 18000);
        JSONArray root = new JSONArray(body);
        JSONArray segments = root.optJSONArray(0);
        if (segments == null) throw new Exception("Unexpected translation response");
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < segments.length(); i++) {
            JSONArray segment = segments.optJSONArray(i);
            if (segment != null && !segment.isNull(0)) result.append(segment.optString(0, ""));
        }
        if (result.toString().trim().isEmpty()) throw new Exception("Empty response");
        return Html.fromHtml(result.toString()).toString();
    }

    private String myMemoryTranslate(String text, String from, String to) throws Exception {
        if (text.length() > 450) throw new Exception("Text section is too long for the backup service");
        String url = "https://api.mymemory.translated.net/get?q=" + URLEncoder.encode(text, "UTF-8")
                + "&langpair=" + from + "%7C" + to;
        String body = httpGet(url, 18000);
        JSONObject json = new JSONObject(body);
        int responseStatus = json.optInt("responseStatus", 200);
        if (responseStatus != 200) throw new Exception(json.optString("responseDetails", "Backup service rejected the request"));
        JSONObject data = json.optJSONObject("responseData");
        String result = data == null ? "" : data.optString("translatedText", "");
        if (result.trim().isEmpty() || result.equalsIgnoreCase("null")) throw new Exception("No translation returned");
        return Html.fromHtml(result).toString();
    }

    private String httpGet(String address, int timeout) throws Exception {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection)new URL(address).openConnection();
            conn.setRequestMethod("GET"); conn.setConnectTimeout(timeout); conn.setReadTimeout(timeout);
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("User-Agent", "AyomideTranslator/2.0 Android");
            int code = conn.getResponseCode();
            InputStream stream = code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream();
            if (stream == null) throw new Exception("Translation service returned HTTP " + code);
            BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
            StringBuilder body = new StringBuilder(); String line;
            while ((line = reader.readLine()) != null) body.append(line);
            reader.close();
            if (code < 200 || code >= 300) throw new Exception("Translation service returned HTTP " + code);
            return body.toString();
        } finally { if (conn != null) conn.disconnect(); }
    }

    private ArrayList<String> splitText(String text, int maxLength) {
        ArrayList<String> parts = new ArrayList<>();
        String remaining = text.trim();
        while (remaining.length() > maxLength) {
            int cut = remaining.lastIndexOf("\n", maxLength);
            if (cut < maxLength / 2) cut = remaining.lastIndexOf(". ", maxLength);
            if (cut < maxLength / 2) cut = remaining.lastIndexOf("! ", maxLength);
            if (cut < maxLength / 2) cut = remaining.lastIndexOf("? ", maxLength);
            if (cut < maxLength / 2) cut = remaining.lastIndexOf(" ", maxLength);
            if (cut <= 0) cut = maxLength;
            else if (remaining.charAt(cut) == ' ') cut++;
            parts.add(remaining.substring(0, cut).trim());
            remaining = remaining.substring(cut).trim();
        }
        if (!remaining.isEmpty()) parts.add(remaining);
        return parts;
    }

    private void swapLanguages() {
        int from = sourceSpinner.getSelectedItemPosition(), to = targetSpinner.getSelectedItemPosition();
        sourceSpinner.setSelection(to); targetSpinner.setSelection(from);
        String oldInput = input.getText().toString();
        String oldOutput = output.getText().toString();
        if (!oldOutput.isEmpty() && !oldOutput.equals("Your translation will appear here.") && !oldOutput.startsWith("Online translation failed")) {
            input.setText(oldOutput); output.setText(oldInput); latestTranslation = oldInput;
        }
    }

    private void startVoiceInput() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, languages.get(sourceSpinner.getSelectedItem().toString()));
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak now");
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
        if (text.isEmpty() || text.equals("Your translation will appear here.")) { toast("Translate something first"); return; }
        if (!ttsReady || tts == null) { toast("Text-to-speech is not ready"); return; }
        String code = languages.get(targetSpinner.getSelectedItem().toString());
        Locale locale = code.equals("zh") ? Locale.SIMPLIFIED_CHINESE : code.equals("en") ? Locale.US : new Locale(code);
        int result = tts.setLanguage(locale);
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            toast("This language voice may need to be installed in your phone's Text-to-Speech settings"); return;
        }
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "ayomide-translation");
    }

    private void copyTranslation() {
        String text = output.getText().toString();
        if (text.isEmpty() || text.equals("Your translation will appear here.")) { toast("Nothing to copy yet"); return; }
        ClipboardManager clipboard = (ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(ClipData.newPlainText("Ayomide translation", text));
        toast("Translation copied");
    }

    private void shareTranslation() {
        String text = output.getText().toString();
        if (text.isEmpty() || text.equals("Your translation will appear here.")) { toast("Nothing to share yet"); return; }
        Intent send = new Intent(Intent.ACTION_SEND); send.setType("text/plain"); send.putExtra(Intent.EXTRA_TEXT, text);
        startActivity(Intent.createChooser(send, "Share translation"));
    }

    private void confirmClearHistory() {
        new AlertDialog.Builder(this).setTitle("Clear translation history?")
                .setMessage("This will remove saved translations from this device.")
                .setNegativeButton("Cancel", null).setPositiveButton("Clear", (d, w) -> {
                    history.clear(); prefs.edit().remove("history").apply(); refreshHistory(); toast("History cleared");
                }).show();
    }

    private void loadHistory() {
        String saved = prefs.getString("history", "");
        if (saved != null && !saved.isEmpty()) {
            for (String item : saved.split("\\n---\\n")) if (!item.trim().isEmpty()) history.add(item);
        }
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
        for (int i = 0; i < history.size(); i++) {
            if (i > 0) b.append("\n\n──────────\n\n");
            b.append(history.get(i));
        }
        historyView.setText(b.toString());
    }

    private TextView text(String value, float size, int color, boolean bold) {
        TextView t = new TextView(this); t.setText(value); t.setTextSize(size); t.setTextColor(color);
        t.setTypeface(chosenTypeface(), bold ? Typeface.BOLD : Typeface.NORMAL);
        return t;
    }

    private Button button(String value, int background, int foreground) {
        Button b = new Button(this); b.setText(value); b.setAllCaps(false); b.setTextSize(13);
        b.setTypeface(chosenTypeface(), Typeface.BOLD); b.setTextColor(foreground);
        b.setBackground(rounded(background, dp(15))); b.setElevation(dp(1));
        b.setPadding(dp(7), dp(3), dp(7), dp(3)); return b;
    }

    private GradientDrawable rounded(int color, int radius, int stroke) {
        GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(radius);
        d.setStroke(dp(1), stroke); return d;
    }

    private GradientDrawable gradient(int start, int end, int radius) {
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{start, end});
        d.setCornerRadius(radius); return d;
    }

    private void toast(String message) { Toast.makeText(this, message, Toast.LENGTH_SHORT).show(); }
    private int dp(int value) { return (int)(value * getResources().getDisplayMetrics().density + 0.5f); }

    @Override protected void onDestroy() {
        if (tts != null) { tts.stop(); tts.shutdown(); }
        executor.shutdownNow(); super.onDestroy();
    }
}