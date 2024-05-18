package com.bhs.zui.album.select;

public interface DragSelectListener {
    boolean isSelected(int index);

    void onSetSelect(int start, int end, boolean select);
}
