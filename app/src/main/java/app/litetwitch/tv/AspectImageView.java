package app.litetwitch.tv;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.ImageView;

/** ImageView с фиксированным соотношением 16:9 по ширине. */
public final class AspectImageView extends ImageView {

    public AspectImageView(Context context) {
        super(context);
    }

    public AspectImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        if (width <= 0) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            return;
        }
        int height = width * 9 / 16;
        setMeasuredDimension(width, height);
    }
}
