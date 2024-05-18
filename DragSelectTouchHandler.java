package com.bhs.zui.album.select;

import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

public class DragSelectTouchHandler implements RecyclerView.OnItemTouchListener {
    public enum Mode {
        RANGE,
        PATH
    }

    private final RecyclerView recyclerView;

    private Mode mode = Mode.RANGE;

    /**
     * 如果第一个被选择的状态是选中的，那么后续的选择状态都是选中的，除非往回拖动选择
     * 比如：
     * 1 0 1 0
     * 四个item，
     * 从第一个开始选，往下拖动，那么后续的选择状态都是选中的，选到第四个时的状态为
     * 1 1 1 1
     * 此时往回拖动，那么第四个的状态会变为未选中
     * 1 1 1 0
     *
     * 如果第一个被选择的状态是未选中的，那么后续的选择状态都是未选中的，即使会退拖动也不会改变选择状态
     * 比如：
     * 0 1 0 1
     * 四个item，
     * 从第一个开始选，往下拖动，那么后续的选择状态都是未选中的，选到第四个时的状态为
     * 0 0 0 0
     * 此时往回拖动，那么第四个的状态还是未选中
     * 0 0 0 0
     */
    private boolean startIsSelected = false;
    private int firstItemPosition = -1;

    private int lastDraggedIndex = -1;
    private boolean dragSelectActive = false;

    private int hotspotHeight;
    private final RVScroller rvScroller = new RVScroller();

    @NonNull
    private final DragSelectListener selectListener;

    public DragSelectTouchHandler(@NonNull RecyclerView rv,
                                  @NonNull DragSelectListener listener) {
        this.recyclerView = rv;
        this.selectListener = listener;
        this.hotspotHeight = dp2px(100);
    }

    public void setActive(boolean active, int firstPos) {
        if (this.dragSelectActive) {
            return;
        }
        this.dragSelectActive = active;
        this.onDragStart(firstPos);

//        log("set drag active: " + active);
    }

    public void setMode(Mode mode) {
        this.mode = mode;
    }

    public void setHotspotHeight(int height) {
        this.hotspotHeight = height;
    }

    private void onDragStart(int itemPosition) {
        // on drag start
        rvScroller.stop();
        firstItemPosition = itemPosition;
        lastDraggedIndex = itemPosition;
        startIsSelected = selectListener.isSelected(itemPosition);
//        log("on drag start, first item position: " + firstItemPosition
//                + ", startIsSelected: " + startIsSelected);
        selectListener.onSetSelect(itemPosition, itemPosition, !startIsSelected);
    }

    private void onDragSelectionStop() {
//        log("on drag stop");
        dragSelectActive = false;
        lastDraggedIndex = -1;
        firstItemPosition = -1;
        rvScroller.stop();
    }

    @Override
    public boolean onInterceptTouchEvent(@NonNull RecyclerView rv, @NonNull MotionEvent e) {
        if (!dragSelectActive) {
            return false;
        }
        RecyclerView.Adapter<?> adapter = rv.getAdapter();
        return adapter != null && adapter.getItemCount() != 0;
    }

    @Override
    public void onTouchEvent(@NonNull RecyclerView rv, @NonNull MotionEvent e) {
        int action = e.getAction();
        int itemPosition = getItemPosition(rv, e);

        if (action == MotionEvent.ACTION_MOVE) {
            if (hotspotHeight > -1) {
                // Check for auto-scroll hotspot
                rvScroller.onMove(e.getY());
            }

            // Drag selection logic
            if (mode == Mode.PATH) {
                if (itemPosition == RecyclerView.NO_POSITION || lastDraggedIndex == itemPosition) {
                    return;
                }
                if (lastDraggedIndex == RecyclerView.NO_POSITION) {
                    // on drag start
                    onDragStart(itemPosition);
                    return;
                }
                lastDraggedIndex = itemPosition;
                selectListener.onSetSelect(itemPosition, itemPosition, !startIsSelected);
                return;
            }

            if (mode == Mode.RANGE) {
                if (itemPosition == RecyclerView.NO_POSITION || lastDraggedIndex == itemPosition) {
                    return;
                }
                if (lastDraggedIndex == RecyclerView.NO_POSITION) {
                    // on drag start
                    onDragStart(itemPosition);
                    return;
                }

                // firstItemPosition 为起始item的位置, 到 itemPosition 之间的item都会被选中
                // 如果 lastDraggedIndex > itemPosition, 则从 itemPosition 到 lastDraggedIndex 之间的item都会取消选中
                if (startIsSelected) {
                    // 全部 unselect
                    if (lastDraggedIndex > itemPosition) {
                        selectListener.onSetSelect(itemPosition, lastDraggedIndex, false);
                    } else {
                        selectListener.onSetSelect(lastDraggedIndex, itemPosition, false);
                    }
                } else {
                    int curStart = Math.min(itemPosition, firstItemPosition);
                    int curEnd = Math.max(itemPosition, firstItemPosition);

                    int lastStart = Math.min(lastDraggedIndex, firstItemPosition);
                    int lastEnd = Math.max(lastDraggedIndex, firstItemPosition);

                    int min = Math.min(curStart, lastStart);
                    int max = Math.max(curEnd, lastEnd);
                    if (min < curStart) {
                        selectListener.onSetSelect(min, curStart-1, false);
                    }
                    if (max > curEnd) {
                        selectListener.onSetSelect(curEnd+1, max, false);
                    }

                    if (curStart < lastStart) {
                        selectListener.onSetSelect(curStart, lastStart-1, true);
                    }
                    if (curEnd > lastEnd) {
                        selectListener.onSetSelect(lastEnd+1, curEnd, true);
                    }
                }
                lastDraggedIndex = itemPosition;
            }
        } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            onDragSelectionStop();
        }
    }

    @Override
    public void onRequestDisallowInterceptTouchEvent(boolean disallowIntercept) {}

    private int getItemPosition(@NonNull RecyclerView rv, @NonNull MotionEvent e) {
        return getItemPosition(rv, e.getX(), e.getY());
    }

    private int getItemPosition(@NonNull RecyclerView rv, float x, float y) {
        View v = rv.findChildViewUnder(x, y);
        if (v == null) {
            return RecyclerView.NO_POSITION;
        }
        return rv.getChildAdapterPosition(v);
    }

    private int dp2px(float dp) {
        float density = recyclerView.getResources().getConfiguration().densityDpi / 160f;
        return (int) (dp * density + 0.5F);
    }

    private class RVScroller implements Runnable {
        private boolean inTopHotspot = false;
        private boolean inBottomHotspot = false;
        private int autoScrollVelocity = 0;

        public void onMove(float y) {
            int topEnd = hotspotHeight;
            int rvHeight = recyclerView.getMeasuredHeight();
            int bottomStart = rvHeight - hotspotHeight;

            if (y <= topEnd) {
                inBottomHotspot = false;
                if (!inTopHotspot) {
                    inTopHotspot = true;
                    //log("Now in TOP hotspot");
                    start();
                }
                autoScrollVelocity = (int) ((y - topEnd) / 2);
                //log("Auto scroll velocity = " + autoScrollVelocity);
            } else if (y >= bottomStart) {
                inTopHotspot = false;
                if (!inBottomHotspot) {
                    inBottomHotspot = true;
                    //log("Now in BOTTOM hotspot");
                    start();
                }
                autoScrollVelocity = (int) ((y - bottomStart) / 2);
                //log("Auto scroll velocity = " + autoScrollVelocity);
            } else if (inTopHotspot || inBottomHotspot) {
                //log("Left the hotspot");
                stop();
            }
        }

        private void start() {
            recyclerView.removeCallbacks(this);
            recyclerView.postOnAnimation(this);
        }

        private void stop() {
            recyclerView.removeCallbacks(this);
            inTopHotspot = false;
            inBottomHotspot = false;
            autoScrollVelocity = 0;
        }

        @Override
        public void run() {
            recyclerView.scrollBy(0, autoScrollVelocity);
            recyclerView.postDelayed(this, 25);
        }
    }
}
