/*
 *     Pupil, Hitomi.la viewer for Android
 *     Copyright (C) 2020  tom5079
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package xyz.quaver.pupil.ui.view;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Vibrator;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.core.content.ContextCompat;
import androidx.core.view.NestedScrollingChild;
import androidx.core.view.NestedScrollingChildHelper;
import androidx.core.view.NestedScrollingParent;
import androidx.core.view.NestedScrollingParentHelper;
import androidx.core.view.ViewCompat;
import androidx.core.widget.TextViewCompat;

import xyz.quaver.pupil.R;

@SuppressWarnings("NullableProblems")
public class SwipePageTurnView extends ViewGroup implements NestedScrollingChild, NestedScrollingParent {

    private static final int PAGE_TURN_LAYOUT_SIZE = 48;
    private static final int PAGE_TURN_ANIM_DURATION = 500;
    private static final int PREV_OFFSET = 64;
    private static final int RIPPLE_GIVE = 4;

    private final float adjustedPageTurnLayoutSize;
    private final float adjustedPrevOffset;
    private final float adjustedRippleGive;
    
    final private NestedScrollingParentHelper mNestedScrollingParentHelper;
    final private NestedScrollingChildHelper mNestedScrollingChildHelper;

    final private Vibrator mVibrator;

    private View mTarget;

    private TextView mPrev;
    private TextView mNext;

    private final Paint mRipplePaint = new Paint();
    private final Rect mRippleBound = new Rect();

    private int mRippleSize = 0;
    private final int mRippleTargetSize;
    private final ValueAnimator mRippleAnimator = new ValueAnimator();

    private int mCurrentOverScroll = 0;

    private int mCurrentPage = 1;
    private boolean mShowPrev;
    private boolean mShowNext;

    private OnPageTurnListener mOnPageTurnListener;

    public SwipePageTurnView(@NonNull Context context) {
        this(context, null);
    }

    public SwipePageTurnView(@NonNull Context context, AttributeSet attr) {
        this(context, attr, 0);
    }

    public SwipePageTurnView(@NonNull Context context, AttributeSet attr, int defStyle) {
        super(context, attr, defStyle);

        setWillNotDraw(false);

        DisplayMetrics metrics = getResources().getDisplayMetrics();

        adjustedPageTurnLayoutSize = PAGE_TURN_LAYOUT_SIZE * metrics.density;
        adjustedPrevOffset = PREV_OFFSET * metrics.density;
        adjustedRippleGive = RIPPLE_GIVE * metrics.density;

        mRippleTargetSize = metrics.widthPixels;

        mNestedScrollingParentHelper = new NestedScrollingParentHelper(this);
        mNestedScrollingChildHelper = new NestedScrollingChildHelper(this);

        mVibrator = (Vibrator) getContext().getSystemService(Context.VIBRATOR_SERVICE);

        mRippleAnimator.addUpdateListener(animation -> {
            mRippleSize = (int) animation.getAnimatedValue();
            invalidate();
        });
        mRippleAnimator.setDuration(PAGE_TURN_ANIM_DURATION);

        initPageTurnView();
    }

    public void setCurrentPage(int currentPage, boolean showNext) {
        mCurrentPage = currentPage;

        mShowPrev = currentPage > 1;
        mShowNext = showNext;

        mPrev.setText(getContext().getString(R.string.main_move_to_page, mCurrentPage-1));
        mNext.setText(getContext().getString(R.string.main_move_to_page, mCurrentPage+1));
    }

    public void setOnPageTurnListener(OnPageTurnListener listener) {
        mOnPageTurnListener = listener;
    }

    private void initPageTurnView() {
        TextView prev = new TextView(getContext());
        TextView next = new TextView(getContext());

        prev.setGravity(Gravity.CENTER_VERTICAL);
        next.setGravity(Gravity.CENTER_VERTICAL);

        prev.setCompoundDrawablesWithIntrinsicBounds(R.drawable.navigate_prev, 0, 0, 0);
        next.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.navigate_next, 0);

        TextViewCompat.setCompoundDrawableTintList(prev, AppCompatResources.getColorStateList(getContext(), R.color.colorAccent));
        TextViewCompat.setCompoundDrawableTintList(next, AppCompatResources.getColorStateList(getContext(), R.color.colorAccent));

        prev.setVisibility(View.INVISIBLE);
        next.setVisibility(View.INVISIBLE);

        mPrev = prev;
        mNext = next;

        addView(mPrev);
        addView(mNext);

        setCurrentPage(1, false);
    }

    private void ensureTarget() {
        if (mTarget == null) {
            for (int i = 0; i < getChildCount(); i++) {
                View child = getChildAt(i);

                if (!child.equals(mNext) && !child.equals(mPrev)) {
                    mTarget = child;
                    break;
                }
            }
        }
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        final int width = getMeasuredWidth();
        final int height = getMeasuredHeight();

        if (getChildCount() == 0)
            return;
        if (mTarget == null)
            ensureTarget();
        if (mTarget == null)
            return;

        mTarget.layout(
            getPaddingLeft(),
            getPaddingTop(),
            width - getPaddingRight(),
            height - getPaddingBottom()
        );

        final int prevWidth = mPrev.getMeasuredWidth();
        mPrev.layout(
            width / 2 - prevWidth / 2,
            getPaddingTop() + (int) adjustedPrevOffset,
            width / 2 + prevWidth / 2,
            getPaddingTop() + (int) adjustedPrevOffset + mPrev.getMeasuredHeight()
        );

        final int nextWidth = mNext.getMeasuredWidth();
        mNext.layout(
            width / 2 - nextWidth / 2,
            height - getPaddingBottom() - mNext.getMeasuredHeight(),
            width / 2 + nextWidth / 2,
            height - getPaddingBottom()
        );
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        if (mTarget == null)
            ensureTarget();
        if (mTarget == null)
            return;

        mTarget.measure(
            MeasureSpec.makeMeasureSpec(getMeasuredWidth() - getPaddingLeft() - getPaddingRight(), MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(getMeasuredHeight() - getPaddingTop() - getPaddingBottom(), MeasureSpec.EXACTLY)
        );

        mPrev.measure(
            MeasureSpec.makeMeasureSpec(getMeasuredWidth(), MeasureSpec.AT_MOST),
            MeasureSpec.makeMeasureSpec((int) adjustedPageTurnLayoutSize, MeasureSpec.EXACTLY)
        );

        mNext.measure(
            MeasureSpec.makeMeasureSpec(getMeasuredWidth(), MeasureSpec.AT_MOST),
            MeasureSpec.makeMeasureSpec((int) adjustedPageTurnLayoutSize, MeasureSpec.EXACTLY)
        );
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (mCurrentOverScroll == 0)
            return;

        if (mCurrentOverScroll > 0) {
            mRippleBound.set(
                getPaddingLeft(),
                (int) (getPaddingTop() - adjustedRippleGive),
                getMeasuredWidth() - getPaddingRight(),
                (int) (getPaddingTop() + adjustedPrevOffset + mPrev.getMeasuredHeight() + adjustedRippleGive)
            );
        }

        if (mCurrentOverScroll < 0) {
            final int height = getMeasuredHeight();
            mRippleBound.set(
                getPaddingLeft(),
                (int) (height - getPaddingBottom() - mNext.getMeasuredHeight() - adjustedRippleGive),
                getMeasuredWidth() - getPaddingRight(),
                height - getPaddingBottom()
            );
        }

        mRipplePaint.reset();
        mRipplePaint.setStyle(Paint.Style.FILL);

        int currentNightMode = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;

        switch (currentNightMode) {
            case Configuration.UI_MODE_NIGHT_YES:
                mRipplePaint.setColor(ContextCompat.getColor(getContext(), R.color.material_light_blue_700));
                break;
            case Configuration.UI_MODE_NIGHT_NO:
                mRipplePaint.setColor(ContextCompat.getColor(getContext(), R.color.material_light_blue_300));
                break;
        }

        canvas.drawCircle(
            (mRippleBound.left + mRippleBound.right) / 2F,
            mCurrentOverScroll > 0 ? mRippleBound.bottom : mRippleBound.top,
            mRippleSize,
            mRipplePaint
        );
    }

    private void onOverscroll(int overscroll) {
        if (mTarget == null)
            ensureTarget();
        if (mTarget == null)
            return;

        mCurrentOverScroll = overscroll;

        if (overscroll > 0) {
            mPrev.setVisibility(View.VISIBLE);
            mNext.setVisibility(View.INVISIBLE);
        } else if (overscroll < 0) {
            mPrev.setVisibility(View.INVISIBLE);
            mNext.setVisibility(View.VISIBLE);
        } else {
            mPrev.setVisibility(View.INVISIBLE);
            mNext.setVisibility(View.INVISIBLE);
        }

        if (Math.abs(overscroll) >= adjustedPageTurnLayoutSize) {
            if (!mRippleAnimator.isStarted() && mRippleSize != mRippleTargetSize) {
                mVibrator.vibrate(10);

                mRippleAnimator.setIntValues(mRippleSize, mRippleTargetSize);
                mRippleAnimator.start();
            }
        } else {
            if (!mRippleAnimator.isStarted() && mRippleSize != 0) {
                mRippleAnimator.setIntValues(mRippleSize, 0);
                mRippleAnimator.start();
            }
        }

        float clippedOverScrollTop = (overscroll > 0 ? 1 : -1) * Math.min(Math.abs(overscroll), adjustedPageTurnLayoutSize);
        mTarget.setTranslationY(clippedOverScrollTop);
    }

    private void onOverscrollEnd(int overscroll) {
        if (mTarget == null)
            ensureTarget();
        if (mTarget == null)
            return;

        mRippleAnimator.cancel();
        mRippleAnimator.setIntValues(mRippleSize, 0);
        mRippleAnimator.start();

        mPrev.setVisibility(View.INVISIBLE);
        mNext.setVisibility(View.INVISIBLE);

        ViewCompat.animate(mTarget)
            .setDuration(PAGE_TURN_ANIM_DURATION)
            .setInterpolator(new DecelerateInterpolator())
            .translationY(0);

        if (Math.abs(overscroll) > adjustedPageTurnLayoutSize && mOnPageTurnListener != null) {
            if (overscroll > 0)
                mOnPageTurnListener.onPrev(mCurrentPage-1);
            if (overscroll < 0)
                mOnPageTurnListener.onNext(mCurrentPage+1);
        }
    }

    // NestedScrollingParent

    private int mTotalUnconsumed = 0;

    @Override
    public boolean onStartNestedScroll(View child, View target, int nestedScrollAxes) {
        return isEnabled() && (nestedScrollAxes & ViewCompat.SCROLL_AXIS_VERTICAL) != 0;
    }

    @Override
    public void onNestedScrollAccepted(View child, View target, int axes) {
        mNestedScrollingParentHelper.onNestedScrollAccepted(child, target, axes);
        startNestedScroll(axes & ViewCompat.SCROLL_AXIS_VERTICAL);

        mTotalUnconsumed = 0;
    }

    @Override
    public void onNestedPreScroll(View target, int dx, int dy, int[] consumed) {
        if (mTotalUnconsumed != 0 && dy > 0 == mTotalUnconsumed > 0) {
            if (Math.abs(dy) > Math.abs(mTotalUnconsumed)) {
                consumed[1] = dy - mTotalUnconsumed;
                mTotalUnconsumed = 0;
            } else {
                mTotalUnconsumed -= dy;
                consumed[1] = dy;
            }

            onOverscroll(mTotalUnconsumed);
        }

        final int[] parentConsumed = new int[2];
        if (dispatchNestedPreScroll(dx - consumed[0], dy - consumed[1], parentConsumed, null)) {
            consumed[0] += parentConsumed[0];
            consumed[1] += parentConsumed[1];
        }
    }

    @Override
    public void onNestedScroll(View target, int dxConsumed, int dyConsumed, int dxUnconsumed, int dyUnconsumed) {
        final int[] mParentOffsetInWindow = new int[2];
        dispatchNestedScroll(dxConsumed, dyConsumed, dxUnconsumed, dyUnconsumed, mParentOffsetInWindow);

        final int dy = dyUnconsumed + mParentOffsetInWindow[1];

        if (mTotalUnconsumed == 0 && ((dy < 0 && !mShowPrev) || (dy > 0 && !mShowNext)))
            return;

        if (dy != 0) {
            mTotalUnconsumed -= dy;
            onOverscroll(mTotalUnconsumed);
        }
    }

    @Override
    public void onStopNestedScroll(View child) {
        mNestedScrollingParentHelper.onStopNestedScroll(child);

        if (Math.abs(mTotalUnconsumed) > 0) {
            onOverscrollEnd(mTotalUnconsumed);
            mTotalUnconsumed = 0;
        }

        stopNestedScroll();
    }

    @Override
    public boolean onNestedPreFling(View target, float velocityX, float velocityY) {
        return dispatchNestedPreFling(velocityX, velocityY);
    }

    @Override
    public boolean onNestedFling(View target, float velocityX, float velocityY, boolean consumed) {
        return dispatchNestedFling(velocityX, velocityY, consumed);
    }

    // NestedScrollingChild

    @Override
    public void setNestedScrollingEnabled(boolean enabled) {
        mNestedScrollingChildHelper.setNestedScrollingEnabled(enabled);
    }

    @Override
    public boolean isNestedScrollingEnabled() {
        return mNestedScrollingChildHelper.isNestedScrollingEnabled();
    }

    @Override
    public boolean startNestedScroll(int axes) {
        return mNestedScrollingChildHelper.startNestedScroll(axes);
    }

    @Override
    public void stopNestedScroll() {
        mNestedScrollingChildHelper.stopNestedScroll();
    }

    @Override
    public boolean hasNestedScrollingParent() {
        return mNestedScrollingChildHelper.hasNestedScrollingParent();
    }

    @Override
    public boolean dispatchNestedScroll(int dxConsumed, int dyConsumed, int dxUnconsumed, int dyUnconsumed, @Nullable int[] offsetInWindow) {
        return mNestedScrollingChildHelper.dispatchNestedScroll(dxConsumed, dyConsumed, dxUnconsumed, dyUnconsumed, offsetInWindow);
    }

    @Override
    public boolean dispatchNestedPreScroll(int dx, int dy, @Nullable int[] consumed, @Nullable int[] offsetInWindow) {
        return mNestedScrollingChildHelper.dispatchNestedPreScroll(dx, dy, consumed, offsetInWindow);
    }

    @Override
    public boolean dispatchNestedFling(float velocityX, float velocityY, boolean consumed) {
        return mNestedScrollingChildHelper.dispatchNestedFling(velocityX, velocityY, consumed);
    }

    @Override
    public boolean dispatchNestedPreFling(float velocityX, float velocityY) {
        return mNestedScrollingChildHelper.dispatchNestedPreFling(velocityX, velocityY);
    }

    public interface OnPageTurnListener {
        void onPrev(int page);
        void onNext(int page);
    }
}
