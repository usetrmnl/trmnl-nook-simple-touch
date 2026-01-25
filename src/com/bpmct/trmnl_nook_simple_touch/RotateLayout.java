package com.bpmct.trmnl_nook_simple_touch;

import android.content.Context;
import android.graphics.Canvas;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

/**
 * Simple pre-Honeycomb rotation container (API 7 compatible).
 */
public class RotateLayout extends ViewGroup {
    private int angle = 0;

    public RotateLayout(Context context) {
        super(context);
        setWillNotDraw(false);
    }

    public void setAngle(int angle) {
        int normalized = angle % 360;
        if (normalized < 0) normalized += 360;
        if (this.angle != normalized) {
            this.angle = normalized;
            requestLayout();
            invalidate();
        }
    }

    public int getAngle() {
        return angle;
    }

    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (getChildCount() == 0) {
            setMeasuredDimension(0, 0);
            return;
        }

        View child = getChildAt(0);
        if (angle == 90 || angle == 270) {
            measureChild(child, heightMeasureSpec, widthMeasureSpec);
            int childWidth = child.getMeasuredWidth();
            int childHeight = child.getMeasuredHeight();
            setMeasuredDimension(childHeight, childWidth);
        } else {
            measureChild(child, widthMeasureSpec, heightMeasureSpec);
            setMeasuredDimension(child.getMeasuredWidth(), child.getMeasuredHeight());
        }
    }

    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        if (getChildCount() == 0) return;
        View child = getChildAt(0);
        if (angle == 90 || angle == 270) {
            child.layout(0, 0, getMeasuredHeight(), getMeasuredWidth());
        } else {
            child.layout(0, 0, getMeasuredWidth(), getMeasuredHeight());
        }
    }

    protected void dispatchDraw(Canvas canvas) {
        if (getChildCount() == 0) {
            return;
        }

        if (angle == 90) {
            canvas.translate(getWidth(), 0);
            canvas.rotate(90);
        } else if (angle == 180) {
            canvas.translate(getWidth(), getHeight());
            canvas.rotate(180);
        } else if (angle == 270) {
            canvas.translate(0, getHeight());
            canvas.rotate(270);
        }

        super.dispatchDraw(canvas);
    }

    public boolean dispatchTouchEvent(MotionEvent event) {
        if (angle == 0) {
            return super.dispatchTouchEvent(event);
        }

        float x = event.getX();
        float y = event.getY();
        float mappedX = x;
        float mappedY = y;
        if (angle == 90) {
            mappedX = y;
            mappedY = getWidth() - x;
        } else if (angle == 180) {
            mappedX = getWidth() - x;
            mappedY = getHeight() - y;
        } else if (angle == 270) {
            mappedX = getHeight() - y;
            mappedY = x;
        }

        android.util.Log.d("TRMNLAPI", "RotateLayout touch raw " + x + "," + y
                + " -> " + mappedX + "," + mappedY
                + " action=" + event.getAction() + " angle=" + angle
                + " size=" + getWidth() + "x" + getHeight());

        MotionEvent transformed = MotionEvent.obtain(event);
        transformed.setLocation(mappedX, mappedY);
        boolean handled = super.dispatchTouchEvent(transformed);
        transformed.recycle();
        return handled;
    }
}
