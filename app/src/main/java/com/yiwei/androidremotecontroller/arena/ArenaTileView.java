package com.yiwei.androidremotecontroller.arena;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.view.View;

public class ArenaTileView extends View {
    private final int coordX;
    private final int coordY;
    private final int idxX;
    private final int idxY;
    private final int size;

    private final Rect mRect;
    private final Paint mBluePaintBrushFill;

    public ArenaTileView(Context context, int coordX, int coordY, int idxX, int idxY, int size) {
        super(context);

        this.coordX = coordX;
        this.coordY = coordY;
        this.idxX = idxX;
        this.idxY = idxY;
        this.size = size;

        mRect = new Rect();
        mRect.set(0, 0, size - 5, size - 5);

        mBluePaintBrushFill = new Paint();
        mBluePaintBrushFill.setColor(Color.rgb(169, 229, 253));
        mBluePaintBrushFill.setAlpha(122);
        mBluePaintBrushFill.setStyle(Paint.Style.FILL);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        canvas.drawRect(mRect, mBluePaintBrushFill);
    }
}
