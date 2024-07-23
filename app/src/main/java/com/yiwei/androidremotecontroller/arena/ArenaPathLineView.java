package com.yiwei.androidremotecontroller.arena;

import static com.yiwei.androidremotecontroller.arena.ArenaView.TILE_SIZE;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.view.View;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** used to draw path lines on arena */
public class ArenaPathLineView extends View {

    private final ArenaView arenaView;
    private final Paint mRedPaintBrushFill;
    // private float[] linePoints;
    private List<Point> linePoints = new ArrayList<>();

    public ArenaPathLineView(Context context, int x, int y, ArenaView arenaView) {
        super(context);

        this.arenaView = arenaView;

        setX(x);
        setY(y);

        mRedPaintBrushFill = new Paint();
        mRedPaintBrushFill.setColor(Color.RED);
        mRedPaintBrushFill.setStyle(Paint.Style.FILL);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (this.linePoints != null) {
            // canvas.drawLines(this.linePoints, mRedPaintBrushFill);
            for (int i = 0; i < this.linePoints.size(); i++) {
                Point from = linePoints.get(i);
                if (i + 1 < linePoints.size()) {
                    Point to = linePoints.get(i + 1);
                    canvas.drawLine(from.x - TILE_SIZE, from.y + TILE_SIZE / 2 - 2, to.x - TILE_SIZE, to.y + TILE_SIZE / 2 - 2, mRedPaintBrushFill);
                }
            }
        }

        // canvas.drawLine(this.getX(), this.getY(), this.getX() + 200, this.getY() + 200, mRedPaintBrushFill);
    }

    public void drawPathLines() {
        if (this.arenaView.calculatedPath == null || this.arenaView.calculatedPath.length() == 0) return;

        JSONArray calculatedPath = this.arenaView.calculatedPath;
        // linePoints = new float[calculatedPath.length() * 2];
        linePoints.clear();

        for (int i = 0; i < calculatedPath.length(); i++) {
            try {
                JSONObject pathObj = calculatedPath.getJSONObject(i);
                ArenaTileView tile = this.arenaView.getTileFromAxis(pathObj.getInt("x"), pathObj.getInt("y"));
                // linePoints[i] = tile.getCoordX();
                // linePoints[i] = tile.getCoordY();
                linePoints.add(new Point(tile.getCoordX(), tile.getCoordY()));
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
        }

        invalidate();
    }
}
