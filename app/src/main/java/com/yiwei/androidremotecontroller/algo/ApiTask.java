package com.yiwei.androidremotecontroller.algo;

import android.os.AsyncTask;
import android.util.Log;

import com.yiwei.androidremotecontroller.arena.ArenaView;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class ApiTask extends AsyncTask<JSONObject, Integer, JSONObject> {

    private final ArenaView arenaView;

    public ApiTask(ArenaView arenaView) {
        this.arenaView = arenaView;
    }

    @Override
    protected JSONObject doInBackground(JSONObject... reqObj) {
        try {
            // URL url = new URL("http://127.0.0.1:5000/path");
            // URL url = new URL("http://192.168.1.142:5000/path");
            URL url = new URL(this.arenaView.mainActivity.getAlgoEXServerIP());
            HttpURLConnection con = (HttpURLConnection)url.openConnection();
            con.setRequestMethod("POST");
            con.setRequestProperty("Content-Type", "application/json");
            con.setRequestProperty("Accept","*/*");
            con.setInstanceFollowRedirects(false);
            con.setDoOutput(true);
            //String jsonInputString = "{\"obstacles\":[{\"x\":7,\"y\":3,\"d\":0,\"id\":10},{\"x\":7,\"y\":11,\"d\":0,\"id\":4},{\"x\":12,\"y\":11,\"d\":0,\"id\":7}],\"robot_x\":1,\"robot_y\":1,\"robot_dir\":0,\"retrying\":false}";

            Log.e("ApiTask", "request: " + reqObj[0].toString());

            try(OutputStream os = con.getOutputStream()) {
                byte[] input = reqObj[0].toString().getBytes("utf-8");
                os.write(input, 0, input.length);
            }

            try(BufferedReader br = new BufferedReader(
                    new InputStreamReader(con.getInputStream(), "utf-8"))) {
                StringBuilder response = new StringBuilder();
                String responseLine = null;
                while ((responseLine = br.readLine()) != null) {
                    response.append(responseLine.trim());
                }

                return new JSONObject(response.toString());
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected void onPostExecute(JSONObject result) {
        try {
            Log.e("ApiTask", "result: " + result.getJSONObject("data").getJSONArray("path"));
            this.arenaView.onApiResult(result.getJSONObject("data"));
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }
}
