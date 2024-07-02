package com.yiwei.androidremotecontroller.arena;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.view.View;

public class ObstacleView extends View {

    private final int id;
    private final int coordX;
    private final int coordY;
    private final int idxX;
    private final int idxY;
    private final int size;

    private final Paint mBlackPaintBrushFill;
    private final Paint mWhitePaintBrushFill;
    private final Rect mRect;

    public ObstacleView(Context context, int id, int coordX, int coordY, int idxX, int idxY, int size) {
        super(context);

        this.id = id;
        this.coordX = coordX;
        this.coordY = coordY;
        this.idxX = idxX;
        this.idxY = idxY;
        this.size = size;

        mBlackPaintBrushFill = new Paint();
        mBlackPaintBrushFill.setColor(Color.BLACK);
        mBlackPaintBrushFill.setStyle(Paint.Style.FILL);

        mWhitePaintBrushFill = new Paint();
        mWhitePaintBrushFill.setColor(Color.WHITE);
        mWhitePaintBrushFill.setStyle(Paint.Style.FILL);
        mWhitePaintBrushFill.setTextSize(16f);

        mRect = new Rect();
        mRect.set(0, 0, size, size);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        canvas.drawRect(mRect, mBlackPaintBrushFill);
        canvas.drawText(String.valueOf(this.id), (float) this.size / 2 - 7, (float) this.size / 2 + 2, this.mWhitePaintBrushFill);
    }

    @Override
    public int getId() {
        return id;
    }

    public int getCoordX() {
        return coordX;
    }

    public int getCoordY() {
        return coordY;
    }

    public int getIdxX() {
        return idxX;
    }

    public int getIdxY() {
        return idxY;
    }

    public int getSize() {
        return size;
    }
}
