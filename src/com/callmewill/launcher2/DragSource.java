/*
 * Copyright (C) 2008 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.callmewill.launcher2;

import android.view.View;

import com.callmewill.launcher2.DropTarget.DragObject;

/**
 * Interface defining an object that can originate a drag.
 * 这个接口定义对象可以被拖动
 * 实现的有：AppsCustomizePagedView,Folder,Workspace
 */
public interface DragSource {
    /**
     * @return whether items dragged from this source supports
     */
    boolean supportsFlingToDelete();

    /**
     * A callback specifically made back to the source after an item from this source has been flung
     * to be deleted on a DropTarget.  In such a situation, this method will be called after
     * onDropCompleted, and more importantly, after the fling animation has completed.
     */
    void onFlingToDeleteCompleted();

    /**
     * A callback made back to the source after an item from this source has been dropped on a
     * DropTarget.
     */
    void onDropCompleted(View target, DragObject d, boolean isFlingToDelete, boolean success);
}
