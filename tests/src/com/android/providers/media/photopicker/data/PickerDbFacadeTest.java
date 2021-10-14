/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.providers.media.photopicker.data;

import static com.google.common.truth.Truth.assertThat;

import android.Manifest;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.provider.CloudMediaProviderContract.AlbumColumns;
import android.provider.CloudMediaProviderContract.MediaColumns;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.providers.media.photopicker.data.model.Category;
import com.android.providers.media.scan.MediaScannerTest.IsolatedContext;

import java.io.File;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class PickerDbFacadeTest {
    private static final long SIZE_BYTES = 7000;
    private static final long DATE_TAKEN_MS = 1623852851911L;
    private static final long DURATION_MS = 5;
    private static final String LOCAL_ID = "50";
    private static final String MEDIA_STORE_URI = "content://media/external/file/" + LOCAL_ID;
    private static final String CLOUD_ID = "asdfghjkl;";
    private static final String VIDEO_MIME_TYPE = "video/mp4";
    private static final String IMAGE_MIME_TYPE = "image/jpeg";

    private static final String LOCAL_PROVIDER = "com.local.provider";
    private static final String CLOUD_PROVIDER = "com.cloud.provider";

    private PickerDbFacade mFacade;
    private Context mContext;

    @Before
    public void setUp() {
        mContext = InstrumentationRegistry.getTargetContext();
        File dbPath = mContext.getDatabasePath(PickerDatabaseHelper.PICKER_DATABASE_NAME);
        dbPath.delete();
        mFacade = new PickerDbFacade(mContext, LOCAL_PROVIDER);
        mFacade.setCloudProvider(CLOUD_PROVIDER);
    }

    @Test
    public void testAddLocalOnly() throws Exception {
        Cursor cursor1 = getLocalMediaCursor(LOCAL_ID, DATE_TAKEN_MS + 1);
        Cursor cursor2 = getLocalMediaCursor(LOCAL_ID, DATE_TAKEN_MS + 2);

        assertThat(mFacade.addMedia(cursor1, LOCAL_PROVIDER)).isEqualTo(1);

        try (Cursor cr = queryMediaAll()) {
            assertThat(cr.getCount()).isEqualTo(1);
            cr.moveToFirst();
            assertMediaCursor(cr, LOCAL_ID, DATE_TAKEN_MS + 1);
        }

        // Test updating the same row
        assertThat(mFacade.addMedia(cursor2, LOCAL_PROVIDER)).isEqualTo(1);

        try (Cursor cr = queryMediaAll()) {
            assertThat(cr.getCount()).isEqualTo(1);
            cr.moveToFirst();
            assertMediaCursor(cr, LOCAL_ID, DATE_TAKEN_MS + 2);
        }
    }

    @Test
    public void testAddCloudPlusLocal() throws Exception {
        Cursor cursor = getCloudMediaCursor(CLOUD_ID, LOCAL_ID, DATE_TAKEN_MS);

        assertThat(mFacade.addMedia(cursor, CLOUD_PROVIDER)).isEqualTo(1);

        try (Cursor cr = queryMediaAll()) {
            assertThat(cr.getCount()).isEqualTo(1);
            cr.moveToFirst();
            assertMediaCursor(cr, CLOUD_ID, DATE_TAKEN_MS);
        }
    }

    @Test
    public void testAddCloudOnly() throws Exception {
        Cursor cursor1 = getCloudMediaCursor(CLOUD_ID, null, DATE_TAKEN_MS + 1);
        Cursor cursor2 = getCloudMediaCursor(CLOUD_ID, null, DATE_TAKEN_MS + 2);

        assertThat(mFacade.addMedia(cursor1, CLOUD_PROVIDER)).isEqualTo(1);

        try (Cursor cr = queryMediaAll()) {
            assertThat(cr.getCount()).isEqualTo(1);
            cr.moveToFirst();
            assertMediaCursor(cr, CLOUD_ID, DATE_TAKEN_MS + 1);
        }

        // Test updating the same row
        assertThat(mFacade.addMedia(cursor2, CLOUD_PROVIDER)).isEqualTo(1);

        try (Cursor cr = queryMediaAll()) {
            assertThat(cr.getCount()).isEqualTo(1);
            cr.moveToFirst();
            assertMediaCursor(cr, CLOUD_ID, DATE_TAKEN_MS + 2);
        }
    }

    @Test
    public void testAddLocalAndCloud_Dedupe() throws Exception {
        Cursor localCursor = getLocalMediaCursor(LOCAL_ID, DATE_TAKEN_MS);
        Cursor cloudCursor = getCloudMediaCursor(CLOUD_ID, LOCAL_ID, DATE_TAKEN_MS + 1);

        assertThat(mFacade.addMedia(localCursor, LOCAL_PROVIDER)).isEqualTo(1);
        assertThat(mFacade.addMedia(cloudCursor, CLOUD_PROVIDER)).isEqualTo(1);

        try (Cursor cr = queryMediaAll()) {
            assertThat(cr.getCount()).isEqualTo(1);
            cr.moveToFirst();
            assertMediaCursor(cr, LOCAL_ID, DATE_TAKEN_MS);
        }
    }

    @Test
    public void testAddCloudAndLocal_Dedupe() throws Exception {
        Cursor localCursor = getLocalMediaCursor(LOCAL_ID, DATE_TAKEN_MS + 1);
        Cursor cloudCursor = getCloudMediaCursor(CLOUD_ID, LOCAL_ID, DATE_TAKEN_MS);

        assertThat(mFacade.addMedia(cloudCursor, CLOUD_PROVIDER)).isEqualTo(1);
        assertThat(mFacade.addMedia(localCursor, LOCAL_PROVIDER)).isEqualTo(1);

        try (Cursor cr = queryMediaAll()) {
            assertThat(cr.getCount()).isEqualTo(1);
            cr.moveToFirst();
            assertMediaCursor(cr, LOCAL_ID, DATE_TAKEN_MS + 1);
        }
    }

    @Test
    public void testRemoveLocal() throws Exception {
        Cursor localCursor = getLocalMediaCursor(LOCAL_ID, DATE_TAKEN_MS);

        assertThat(mFacade.addMedia(localCursor, LOCAL_PROVIDER)).isEqualTo(1);

        try (Cursor cr = queryMediaAll()) {
            assertThat(cr.getCount()).isEqualTo(1);
        }

        assertThat(mFacade.removeMedia(getDeletedMediaCursor(LOCAL_ID), 0, LOCAL_PROVIDER))
                .isEqualTo(1);

        try (Cursor cr = queryMediaAll()) {
            assertThat(cr.getCount()).isEqualTo(0);
        }
    }

    @Test
    public void testRemoveLocal_promote() throws Exception {
        Cursor localCursor = getLocalMediaCursor(LOCAL_ID, DATE_TAKEN_MS);
        Cursor cloudCursor = getCloudMediaCursor(CLOUD_ID, LOCAL_ID, DATE_TAKEN_MS);

        assertThat(mFacade.addMedia(localCursor, LOCAL_PROVIDER)).isEqualTo(1);
        assertThat(mFacade.addMedia(cloudCursor, CLOUD_PROVIDER)).isEqualTo(1);

        try (Cursor cr = queryMediaAll()) {
            assertThat(cr.getCount()).isEqualTo(1);
            cr.moveToFirst();
            assertMediaCursor(cr, LOCAL_ID, DATE_TAKEN_MS);
        }

        assertThat(mFacade.removeMedia(getDeletedMediaCursor(LOCAL_ID), 0, LOCAL_PROVIDER))
                .isEqualTo(1);

        try (Cursor cr = queryMediaAll()) {
            assertThat(cr.getCount()).isEqualTo(1);
            cr.moveToFirst();
            assertMediaCursor(cr, CLOUD_ID, DATE_TAKEN_MS);
        }
    }

    @Test
    public void testRemoveCloud() throws Exception {
        Cursor cloudCursor = getCloudMediaCursor(CLOUD_ID, LOCAL_ID, DATE_TAKEN_MS);

        assertThat(mFacade.addMedia(cloudCursor, CLOUD_PROVIDER)).isEqualTo(1);

        try (Cursor cr = queryMediaAll()) {
            assertThat(cr.getCount()).isEqualTo(1);
        }

        assertThat(mFacade.removeMedia(getDeletedMediaCursor(CLOUD_ID), 0, CLOUD_PROVIDER))
                .isEqualTo(1);

        try (Cursor cr = queryMediaAll()) {
            assertThat(cr.getCount()).isEqualTo(0);
        }
    }

    @Test
    public void testRemoveCloud_promote() throws Exception {
        Cursor cloudCursor1 = getCloudMediaCursor(CLOUD_ID + "1", LOCAL_ID, DATE_TAKEN_MS + 1);
        Cursor cloudCursor2 = getCloudMediaCursor(CLOUD_ID + "2", LOCAL_ID, DATE_TAKEN_MS + 2);

        assertThat(mFacade.addMedia(cloudCursor1, CLOUD_PROVIDER)).isEqualTo(1);
        assertThat(mFacade.addMedia(cloudCursor2, CLOUD_PROVIDER)).isEqualTo(1);

        try (Cursor cr = queryMediaAll()) {
            assertThat(cr.getCount()).isEqualTo(1);
            cr.moveToFirst();
            assertMediaCursor(cr, CLOUD_ID + "1", DATE_TAKEN_MS + 1);
        }

        assertThat(mFacade.removeMedia(getDeletedMediaCursor(CLOUD_ID + "1"), 0, CLOUD_PROVIDER))
                .isEqualTo(1);

        try (Cursor cr = queryMediaAll()) {
            assertThat(cr.getCount()).isEqualTo(1);
            cr.moveToFirst();
            assertMediaCursor(cr, CLOUD_ID + "2", DATE_TAKEN_MS + 2);
        }
    }

    @Test
    public void testRemoveHidden() throws Exception {
        Cursor localCursor = getLocalMediaCursor(LOCAL_ID, DATE_TAKEN_MS);
        Cursor cloudCursor = getCloudMediaCursor(CLOUD_ID, LOCAL_ID, DATE_TAKEN_MS);

        assertThat(mFacade.addMedia(cloudCursor, CLOUD_PROVIDER)).isEqualTo(1);
        assertThat(mFacade.addMedia(localCursor, LOCAL_PROVIDER)).isEqualTo(1);

        try (Cursor cr = queryMediaAll()) {
            assertThat(cr.getCount()).isEqualTo(1);
            cr.moveToFirst();
            assertMediaCursor(cr, LOCAL_ID, DATE_TAKEN_MS);
        }

        assertThat(mFacade.removeMedia(getDeletedMediaCursor(CLOUD_ID), 0, CLOUD_PROVIDER))
                .isEqualTo(1);

        try (Cursor cr = queryMediaAll()) {
            assertThat(cr.getCount()).isEqualTo(1);
            cr.moveToFirst();
            assertMediaCursor(cr, LOCAL_ID, DATE_TAKEN_MS);
        }
    }


    @Test
    public void testLocalUpdate() throws Exception {
        Cursor localCursor1 = getLocalMediaCursor(LOCAL_ID, DATE_TAKEN_MS + 1);
        Cursor localCursor2 = getLocalMediaCursor(LOCAL_ID, DATE_TAKEN_MS + 2);

        assertThat(mFacade.addMedia(localCursor1, LOCAL_PROVIDER)).isEqualTo(1);
        assertThat(mFacade.addMedia(localCursor2, LOCAL_PROVIDER)).isEqualTo(1);

        try (Cursor cr = queryMediaAll()) {
            assertThat(cr.getCount()).isEqualTo(1);
            cr.moveToFirst();
            assertMediaCursor(cr, LOCAL_ID, DATE_TAKEN_MS + 2);
        }

        assertThat(mFacade.removeMedia(getDeletedMediaCursor(LOCAL_ID), 0, LOCAL_PROVIDER))
                .isEqualTo(1);

        try (Cursor cr = queryMediaAll()) {
            assertThat(cr.getCount()).isEqualTo(0);
        }
    }

    @Test
    public void testCloudUpdate_withoutLocal() throws Exception {
        Cursor cloudCursor1 = getCloudMediaCursor(CLOUD_ID, LOCAL_ID, DATE_TAKEN_MS + 1);
        Cursor cloudCursor2 = getCloudMediaCursor(CLOUD_ID, LOCAL_ID, DATE_TAKEN_MS + 2);

        assertThat(mFacade.addMedia(cloudCursor1, CLOUD_PROVIDER)).isEqualTo(1);
        assertThat(mFacade.addMedia(cloudCursor2, CLOUD_PROVIDER)).isEqualTo(1);

        try (Cursor cr = queryMediaAll()) {
            assertThat(cr.getCount()).isEqualTo(1);
            cr.moveToFirst();
            assertMediaCursor(cr, CLOUD_ID, DATE_TAKEN_MS + 2);
        }

        assertThat(mFacade.removeMedia(getDeletedMediaCursor(CLOUD_ID), 0, CLOUD_PROVIDER))
                .isEqualTo(1);

        try (Cursor cr = queryMediaAll()) {
            assertThat(cr.getCount()).isEqualTo(0);
        }
    }

    @Test
    public void testCloudUpdate_withLocal() throws Exception {
        Cursor localCursor = getLocalMediaCursor(LOCAL_ID, DATE_TAKEN_MS);
        Cursor cloudCursor1 = getCloudMediaCursor(CLOUD_ID, LOCAL_ID, DATE_TAKEN_MS + 1);
        Cursor cloudCursor2 = getCloudMediaCursor(CLOUD_ID, LOCAL_ID, DATE_TAKEN_MS + 2);

        assertThat(mFacade.addMedia(localCursor, LOCAL_PROVIDER)).isEqualTo(1);
        assertThat(mFacade.addMedia(cloudCursor1, CLOUD_PROVIDER)).isEqualTo(1);
        assertThat(mFacade.addMedia(cloudCursor2, CLOUD_PROVIDER)).isEqualTo(1);

        try (Cursor cr = queryMediaAll()) {
            assertThat(cr.getCount()).isEqualTo(1);
            cr.moveToFirst();
            assertMediaCursor(cr, LOCAL_ID, DATE_TAKEN_MS);
        }

        assertThat(mFacade.removeMedia(getDeletedMediaCursor(LOCAL_ID), 0, LOCAL_PROVIDER))
                .isEqualTo(1);

        try (Cursor cr = queryMediaAll()) {
            assertThat(cr.getCount()).isEqualTo(1);
            cr.moveToFirst();
            assertMediaCursor(cr, CLOUD_ID, DATE_TAKEN_MS + 2);
        }

        assertThat(mFacade.removeMedia(getDeletedMediaCursor(CLOUD_ID), 0, CLOUD_PROVIDER))
                .isEqualTo(1);

        try (Cursor cr = queryMediaAll()) {
            assertThat(cr.getCount()).isEqualTo(0);
        }
    }

    @Test
    public void testResetLocal() throws Exception {
        Cursor localCursor = getLocalMediaCursor(LOCAL_ID, DATE_TAKEN_MS);
        // Add two cloud_ids mapping to the same local_id to verify that
        // only one gets promoted
        Cursor cloudCursor1 = getCloudMediaCursor(CLOUD_ID + "1", LOCAL_ID, DATE_TAKEN_MS);
        Cursor cloudCursor2 = getCloudMediaCursor(CLOUD_ID + "2", LOCAL_ID, DATE_TAKEN_MS);

        assertThat(mFacade.addMedia(localCursor, LOCAL_PROVIDER)).isEqualTo(1);
        assertThat(mFacade.addMedia(cloudCursor1, CLOUD_PROVIDER)).isEqualTo(1);
        assertThat(mFacade.addMedia(cloudCursor2, CLOUD_PROVIDER)).isEqualTo(1);

        try (Cursor cr = queryMediaAll()) {
            assertThat(cr.getCount()).isEqualTo(1);
            cr.moveToFirst();
            assertMediaCursor(cr, LOCAL_ID, DATE_TAKEN_MS);
        }

        assertThat(mFacade.resetMedia(LOCAL_PROVIDER)).isEqualTo(1);

        try (Cursor cr = queryMediaAll()) {
            assertThat(cr.getCount()).isEqualTo(1);
            cr.moveToFirst();

            // Verify that local_id was deleted and either of cloudCursor1 or cloudCursor2
            // was promoted
            assertThat(cr.getString(1)).isNotNull();
        }
    }

    @Test
    public void testResetCloud() throws Exception {
        Cursor localCursor = getLocalMediaCursor(LOCAL_ID, DATE_TAKEN_MS);
        Cursor cloudCursor = getCloudMediaCursor(CLOUD_ID, LOCAL_ID, DATE_TAKEN_MS);

        assertThat(mFacade.addMedia(localCursor, LOCAL_PROVIDER)).isEqualTo(1);
        assertThat(mFacade.addMedia(cloudCursor, CLOUD_PROVIDER)).isEqualTo(1);

        try (Cursor cr = queryMediaAll()) {
            assertThat(cr.getCount()).isEqualTo(1);
            cr.moveToFirst();
            assertMediaCursor(cr, LOCAL_ID, DATE_TAKEN_MS);
        }

        assertThat(mFacade.resetMedia(CLOUD_PROVIDER)).isEqualTo(1);

        try (Cursor cr = queryMediaAll()) {
            assertThat(cr.getCount()).isEqualTo(1);
            cr.moveToFirst();
            assertMediaCursor(cr, LOCAL_ID, DATE_TAKEN_MS);
        }
    }

    @Test
    public void testQueryWithDateTakenFilter() throws Exception {
        Cursor localCursor = getLocalMediaCursor(LOCAL_ID, DATE_TAKEN_MS);
        Cursor cloudCursor = getCloudMediaCursor(CLOUD_ID, LOCAL_ID, DATE_TAKEN_MS);

        assertThat(mFacade.addMedia(localCursor, LOCAL_PROVIDER)).isEqualTo(1);
        assertThat(mFacade.addMedia(cloudCursor, CLOUD_PROVIDER)).isEqualTo(1);

        try (Cursor cr = queryMediaAll()) {
            assertThat(cr.getCount()).isEqualTo(1);
        }

        PickerDbFacade.QueryFilterBuilder qfbBefore = new PickerDbFacade.QueryFilterBuilder(5);
        qfbBefore.setDateTakenBeforeMs(DATE_TAKEN_MS - 1);
        qfbBefore.setId(5);
        try (Cursor cr = mFacade.queryMedia(qfbBefore.build())) {
            assertThat(cr.getCount()).isEqualTo(0);
        }

        PickerDbFacade.QueryFilterBuilder qfbAfter = new PickerDbFacade.QueryFilterBuilder(5);
        qfbAfter.setDateTakenAfterMs(DATE_TAKEN_MS + 1);
        qfbAfter.setId(5);
        try (Cursor cr = mFacade.queryMedia(qfbAfter.build())) {
            assertThat(cr.getCount()).isEqualTo(0);
        }
    }

    @Test
    public void testQueryWithIdFilter() throws Exception {
        Cursor cursor1 = getLocalMediaCursor(LOCAL_ID + "1", DATE_TAKEN_MS);
        Cursor cursor2 = getLocalMediaCursor(LOCAL_ID + "2", DATE_TAKEN_MS);

        assertThat(mFacade.addMedia(cursor1, LOCAL_PROVIDER)).isEqualTo(1);
        assertThat(mFacade.addMedia(cursor2, LOCAL_PROVIDER)).isEqualTo(1);

        PickerDbFacade.QueryFilterBuilder qfbBefore = new PickerDbFacade.QueryFilterBuilder(5);
        qfbBefore.setDateTakenBeforeMs(DATE_TAKEN_MS);
        qfbBefore.setId(2);
        try (Cursor cr = mFacade.queryMedia(qfbBefore.build())) {
            assertThat(cr.getCount()).isEqualTo(1);

            cr.moveToFirst();
            assertMediaCursor(cr, LOCAL_ID + "1", DATE_TAKEN_MS);
        }

        PickerDbFacade.QueryFilterBuilder qfbAfter = new PickerDbFacade.QueryFilterBuilder(5);
        qfbAfter.setDateTakenAfterMs(DATE_TAKEN_MS);
        qfbAfter.setId(1);
        try (Cursor cr = mFacade.queryMedia(qfbAfter.build())) {
            assertThat(cr.getCount()).isEqualTo(1);

            cr.moveToFirst();
            assertMediaCursor(cr, LOCAL_ID + "2", DATE_TAKEN_MS);
        }
    }

    @Test
    public void testQueryWithLimit() throws Exception {
        Cursor cursor1 = getLocalMediaCursor(LOCAL_ID + "1", DATE_TAKEN_MS);
        Cursor cursor2 = getCloudMediaCursor(CLOUD_ID + "2", null, DATE_TAKEN_MS);
        Cursor cursor3 = getLocalMediaCursor(LOCAL_ID + "3", DATE_TAKEN_MS);

        assertThat(mFacade.addMedia(cursor1, LOCAL_PROVIDER)).isEqualTo(1);
        assertThat(mFacade.addMedia(cursor2, CLOUD_PROVIDER)).isEqualTo(1);
        assertThat(mFacade.addMedia(cursor3, LOCAL_PROVIDER)).isEqualTo(1);

        PickerDbFacade.QueryFilterBuilder qfbBefore = new PickerDbFacade.QueryFilterBuilder(1);
        qfbBefore.setDateTakenBeforeMs(DATE_TAKEN_MS + 1);
        qfbBefore.setId(0);
        try (Cursor cr = mFacade.queryMedia(qfbBefore.build())) {
            assertThat(cr.getCount()).isEqualTo(1);

            cr.moveToFirst();
            assertMediaCursor(cr, LOCAL_ID + "3", DATE_TAKEN_MS);
        }

        PickerDbFacade.QueryFilterBuilder qfbAfter = new PickerDbFacade.QueryFilterBuilder(1);
        qfbAfter.setDateTakenAfterMs(DATE_TAKEN_MS - 1);
        qfbAfter.setId(0);
        try (Cursor cr = mFacade.queryMedia(qfbAfter.build())) {
            assertThat(cr.getCount()).isEqualTo(1);

            cr.moveToFirst();
            assertMediaCursor(cr, LOCAL_ID + "3", DATE_TAKEN_MS);
        }

        try (Cursor cr = mFacade.queryMedia(new PickerDbFacade.QueryFilterBuilder(1).build())) {
            assertThat(cr.getCount()).isEqualTo(1);

            cr.moveToFirst();
            assertMediaCursor(cr, LOCAL_ID + "3", DATE_TAKEN_MS);
        }
    }

    @Test
    public void testQueryWithSizeFilter() throws Exception {
        Cursor cursor1 = getMediaCursor(LOCAL_ID, DATE_TAKEN_MS,
                /* mediaStoreUri */ null, /* sizeBytes */ 1, VIDEO_MIME_TYPE,
                /* isFavorite */ false);
        Cursor cursor2 = getMediaCursor(CLOUD_ID, DATE_TAKEN_MS,
                /* mediaStoreUri */ null, /* sizeBytes */ 2, VIDEO_MIME_TYPE,
                /* isFavorite */ false);

        assertThat(mFacade.addMedia(cursor1, LOCAL_PROVIDER)).isEqualTo(1);
        assertThat(mFacade.addMedia(cursor2, CLOUD_PROVIDER)).isEqualTo(1);

        // Verify all
        PickerDbFacade.QueryFilterBuilder qfbAll = new PickerDbFacade.QueryFilterBuilder(1000);
        qfbAll.setSizeBytes(10);
        try (Cursor cr = mFacade.queryMedia(qfbAll.build())) {
            assertThat(cr.getCount()).isEqualTo(2);
        }

        qfbAll.setSizeBytes(1);
        try (Cursor cr = mFacade.queryMedia(qfbAll.build())) {
            assertThat(cr.getCount()).isEqualTo(1);

            cr.moveToFirst();
            assertMediaCursor(cr, LOCAL_ID);
        }

        // Verify after
        PickerDbFacade.QueryFilterBuilder qfbAfter = new PickerDbFacade.QueryFilterBuilder(1000);
        qfbAfter.setDateTakenAfterMs(DATE_TAKEN_MS - 1);
        qfbAfter.setId(0);
        qfbAfter.setSizeBytes(10);
        try (Cursor cr = mFacade.queryMedia(qfbAfter.build())) {
            assertThat(cr.getCount()).isEqualTo(2);
        }

        qfbAfter.setDateTakenAfterMs(DATE_TAKEN_MS - 1);
        qfbAfter.setId(0);
        qfbAfter.setSizeBytes(1);
        try (Cursor cr = mFacade.queryMedia(qfbAfter.build())) {
            assertThat(cr.getCount()).isEqualTo(1);

            cr.moveToFirst();
            assertMediaCursor(cr, LOCAL_ID);
        }

        // Verify before
        PickerDbFacade.QueryFilterBuilder qfbBefore = new PickerDbFacade.QueryFilterBuilder(1000);
        qfbBefore.setDateTakenBeforeMs(DATE_TAKEN_MS + 1);
        qfbBefore.setId(0);
        qfbBefore.setSizeBytes(10);
        try (Cursor cr = mFacade.queryMedia(qfbBefore.build())) {
            assertThat(cr.getCount()).isEqualTo(2);
        }

        qfbBefore.setDateTakenBeforeMs(DATE_TAKEN_MS + 1);
        qfbBefore.setId(0);
        qfbBefore.setSizeBytes(1);
        try (Cursor cr = mFacade.queryMedia(qfbBefore.build())) {
            assertThat(cr.getCount()).isEqualTo(1);

            cr.moveToFirst();
            assertMediaCursor(cr, LOCAL_ID);
        }
    }

    @Test
    public void testQueryWithMimeTypeFilter() throws Exception {
        Cursor cursor1 = getMediaCursor(LOCAL_ID, DATE_TAKEN_MS,
                /* mediaStoreUri */ null, SIZE_BYTES, "video/webm", /* isFavorite */ false);
        Cursor cursor2 = getMediaCursor(CLOUD_ID, DATE_TAKEN_MS,
                /* mediaStoreUri */ null, SIZE_BYTES, "video/mp4", /* isFavorite */ false);

        assertThat(mFacade.addMedia(cursor1, LOCAL_PROVIDER)).isEqualTo(1);
        assertThat(mFacade.addMedia(cursor2, CLOUD_PROVIDER)).isEqualTo(1);

        // Verify all
        PickerDbFacade.QueryFilterBuilder qfbAll = new PickerDbFacade.QueryFilterBuilder(1000);
        qfbAll.setMimeType("*/*");
        try (Cursor cr = mFacade.queryMedia(qfbAll.build())) {
            assertThat(cr.getCount()).isEqualTo(2);
        }

        qfbAll.setMimeType("video/mp4");
        try (Cursor cr = mFacade.queryMedia(qfbAll.build())) {
            assertThat(cr.getCount()).isEqualTo(1);

            cr.moveToFirst();
            assertMediaCursor(cr, CLOUD_ID, DATE_TAKEN_MS);
        }

        // Verify after
        PickerDbFacade.QueryFilterBuilder qfbAfter = new PickerDbFacade.QueryFilterBuilder(1000);
        qfbAfter.setDateTakenAfterMs(DATE_TAKEN_MS - 1);
        qfbAfter.setId(0);
        qfbAfter.setMimeType("video/*");
        try (Cursor cr = mFacade.queryMedia(qfbAfter.build())) {
            assertThat(cr.getCount()).isEqualTo(2);
        }

        qfbAfter.setDateTakenAfterMs(DATE_TAKEN_MS - 1);
        qfbAfter.setId(0);
        qfbAfter.setMimeType("video/webm");
        try (Cursor cr = mFacade.queryMedia(qfbAfter.build())) {
            assertThat(cr.getCount()).isEqualTo(1);

            cr.moveToFirst();
            assertMediaCursor(cr, LOCAL_ID);
        }

        // Verify before
        PickerDbFacade.QueryFilterBuilder qfbBefore = new PickerDbFacade.QueryFilterBuilder(1000);
        qfbBefore.setDateTakenBeforeMs(DATE_TAKEN_MS + 1);
        qfbBefore.setId(0);
        qfbBefore.setMimeType("video/*");
        try (Cursor cr = mFacade.queryMedia(qfbBefore.build())) {
            assertThat(cr.getCount()).isEqualTo(2);
        }

        qfbBefore.setDateTakenBeforeMs(DATE_TAKEN_MS + 1);
        qfbBefore.setId(0);
        qfbBefore.setMimeType("video/mp4");
        try (Cursor cr = mFacade.queryMedia(qfbBefore.build())) {
            assertThat(cr.getCount()).isEqualTo(1);

            cr.moveToFirst();
            assertMediaCursor(cr, CLOUD_ID, DATE_TAKEN_MS);
        }
    }

    @Test
    public void testQueryWithSizeAndMimeTypeFilter() throws Exception {
        Cursor cursor1 = getMediaCursor(LOCAL_ID, DATE_TAKEN_MS,
                /* mediaStoreUri */ null, /* sizeBytes */ 2, "video/webm", /* isFavorite */ false);
        Cursor cursor2 = getMediaCursor(CLOUD_ID, DATE_TAKEN_MS,
                /* mediaStoreUri */ null, /* sizeBytes */ 1, "video/mp4", /* isFavorite */ false);

        assertThat(mFacade.addMedia(cursor1, LOCAL_PROVIDER)).isEqualTo(1);
        assertThat(mFacade.addMedia(cursor2, CLOUD_PROVIDER)).isEqualTo(1);

        // mime_type and size filter matches all
        PickerDbFacade.QueryFilterBuilder qfbAll = new PickerDbFacade.QueryFilterBuilder(1000);
        qfbAll.setMimeType("*/*");
        qfbAll.setSizeBytes(10);
        try (Cursor cr = mFacade.queryMedia(qfbAll.build())) {
            assertThat(cr.getCount()).isEqualTo(2);
        }

        // mime_type and size filter matches none
        qfbAll.setMimeType("video/webm");
        qfbAll.setSizeBytes(1);
        try (Cursor cr = mFacade.queryMedia(qfbAll.build())) {
            assertThat(cr.getCount()).isEqualTo(0);
        }
    }

    @Test
    public void testSetCloudProvider() throws Exception {
        Cursor localCursor = getLocalMediaCursor(LOCAL_ID, DATE_TAKEN_MS);
        Cursor cloudCursor = getCloudMediaCursor(CLOUD_ID, null, DATE_TAKEN_MS);

        assertThat(mFacade.addMedia(localCursor, LOCAL_PROVIDER)).isEqualTo(1);
        assertThat(mFacade.addMedia(cloudCursor, CLOUD_PROVIDER)).isEqualTo(1);

        try (Cursor cr = queryMediaAll()) {
            assertThat(cr.getCount()).isEqualTo(2);

            cr.moveToFirst();
            assertMediaCursor(cr, CLOUD_ID, DATE_TAKEN_MS);

            cr.moveToNext();
            assertMediaCursor(cr, LOCAL_ID, DATE_TAKEN_MS);
        }

        // Clearing the cloud provider hides cloud media
        mFacade.setCloudProvider(null);

        try (Cursor cr = queryMediaAll()) {
            assertThat(cr.getCount()).isEqualTo(1);

            cr.moveToFirst();
            assertMediaCursor(cr, LOCAL_ID, DATE_TAKEN_MS);
        }

        // Setting the cloud provider unhides cloud media
        mFacade.setCloudProvider(CLOUD_PROVIDER);

        try (Cursor cr = queryMediaAll()) {
            assertThat(cr.getCount()).isEqualTo(2);

            cr.moveToFirst();
            assertMediaCursor(cr, CLOUD_ID, DATE_TAKEN_MS);

            cr.moveToNext();
            assertMediaCursor(cr, LOCAL_ID, DATE_TAKEN_MS);
        }
    }

    @Test
    public void testFavorites() throws Exception {
        Cursor localCursor1 = getMediaCursor(LOCAL_ID + "1", DATE_TAKEN_MS,
                /* mediaStoreUri */ null, SIZE_BYTES, VIDEO_MIME_TYPE, /* isFavorite */ true);
        Cursor localCursor2 = getMediaCursor(LOCAL_ID + "2", DATE_TAKEN_MS,
                /* mediaStoreUri */ null, SIZE_BYTES, VIDEO_MIME_TYPE, /* isFavorite */ false);
        Cursor cloudCursor1 = getMediaCursor(CLOUD_ID + "1", DATE_TAKEN_MS,
                /* mediaStoreUri */ null, SIZE_BYTES, VIDEO_MIME_TYPE, /* isFavorite */ true);
        Cursor cloudCursor2 = getMediaCursor(CLOUD_ID + "2", DATE_TAKEN_MS,
                /* mediaStoreUri */ null, SIZE_BYTES, VIDEO_MIME_TYPE, /* isFavorite */ false);

        assertThat(mFacade.addMedia(localCursor1, LOCAL_PROVIDER)).isEqualTo(1);
        assertThat(mFacade.addMedia(localCursor2, LOCAL_PROVIDER)).isEqualTo(1);
        assertThat(mFacade.addMedia(cloudCursor1, CLOUD_PROVIDER)).isEqualTo(1);
        assertThat(mFacade.addMedia(cloudCursor2, CLOUD_PROVIDER)).isEqualTo(1);

        PickerDbFacade.QueryFilterBuilder qfb =
                new PickerDbFacade.QueryFilterBuilder(/* limit */ 1000);
        try (Cursor cr = mFacade.queryMedia(qfb.build())) {
            assertThat(cr.getCount()).isEqualTo(4);
        }

        qfb.setIsFavorite(true);
        try (Cursor cr = mFacade.queryMedia(qfb.build())) {
            assertThat(cr.getCount()).isEqualTo(2);
            cr.moveToFirst();
            assertMediaCursor(cr, CLOUD_ID + 1, DATE_TAKEN_MS);

            cr.moveToNext();
            assertMediaCursor(cr, LOCAL_ID + 1, DATE_TAKEN_MS);
        }
    }

    @Test
    public void testGetFavoritesAlbumWithoutFilter() throws Exception {
        Cursor localCursor1 = getMediaCursor(LOCAL_ID + "1", DATE_TAKEN_MS,
                /* mediaStoreUri */ null, SIZE_BYTES, VIDEO_MIME_TYPE, /* isFavorite */ true);
        Cursor localCursor2 = getMediaCursor(LOCAL_ID + "2", DATE_TAKEN_MS,
                /* mediaStoreUri */ null, SIZE_BYTES, IMAGE_MIME_TYPE, /* isFavorite */ false);
        Cursor cloudCursor1 = getMediaCursor(CLOUD_ID + "1", DATE_TAKEN_MS,
                /* mediaStoreUri */ null, SIZE_BYTES, IMAGE_MIME_TYPE, /* isFavorite */ true);
        Cursor cloudCursor2 = getMediaCursor(CLOUD_ID + "2", DATE_TAKEN_MS,
                /* mediaStoreUri */ null, SIZE_BYTES, VIDEO_MIME_TYPE, /* isFavorite */ false);

        assertThat(mFacade.addMedia(localCursor1, LOCAL_PROVIDER)).isEqualTo(1);
        assertThat(mFacade.addMedia(localCursor2, LOCAL_PROVIDER)).isEqualTo(1);
        assertThat(mFacade.addMedia(cloudCursor1, CLOUD_PROVIDER)).isEqualTo(1);
        assertThat(mFacade.addMedia(cloudCursor2, CLOUD_PROVIDER)).isEqualTo(1);

        PickerDbFacade.QueryFilterBuilder qfb =
                new PickerDbFacade.QueryFilterBuilder(/* limit */ 1000);
        try (Cursor cr = mFacade.queryMedia(qfb.build())) {
            assertThat(cr.getCount()).isEqualTo(4);
        }

        try (Cursor cr = mFacade.getFavoriteAlbum(qfb.build())) {
            assertThat(cr.getCount()).isEqualTo(1);
            cr.moveToFirst();
            assertAlbumCursor(cr,
                    Category.CATEGORY_FAVORITES,
                    Category.getCategoryName(mContext, Category.CATEGORY_FAVORITES),
                    LOCAL_ID + "1",
                    DATE_TAKEN_MS,
                    /* count */ 2);
        }
    }

    @Test
    public void testGetFavoritesAlbumWithMimeTypeFilter() throws Exception {
        Cursor localCursor1 = getMediaCursor(LOCAL_ID + "1", DATE_TAKEN_MS,
                /* mediaStoreUri */ null, SIZE_BYTES, VIDEO_MIME_TYPE, /* isFavorite */ true);
        Cursor localCursor2 = getMediaCursor(LOCAL_ID + "2", DATE_TAKEN_MS,
                /* mediaStoreUri */ null, SIZE_BYTES, IMAGE_MIME_TYPE, /* isFavorite */ false);
        Cursor cloudCursor1 = getMediaCursor(CLOUD_ID + "1", DATE_TAKEN_MS,
                /* mediaStoreUri */ null, SIZE_BYTES, IMAGE_MIME_TYPE, /* isFavorite */ true);
        Cursor cloudCursor2 = getMediaCursor(CLOUD_ID + "2", DATE_TAKEN_MS,
                /* mediaStoreUri */ null, SIZE_BYTES, VIDEO_MIME_TYPE, /* isFavorite */ false);

        assertThat(mFacade.addMedia(localCursor1, LOCAL_PROVIDER)).isEqualTo(1);
        assertThat(mFacade.addMedia(localCursor2, LOCAL_PROVIDER)).isEqualTo(1);
        assertThat(mFacade.addMedia(cloudCursor1, CLOUD_PROVIDER)).isEqualTo(1);
        assertThat(mFacade.addMedia(cloudCursor2, CLOUD_PROVIDER)).isEqualTo(1);

        PickerDbFacade.QueryFilterBuilder qfb =
                new PickerDbFacade.QueryFilterBuilder(/* limit */ 1000);
        try (Cursor cr = mFacade.queryMedia(qfb.build())) {
            assertThat(cr.getCount()).isEqualTo(4);
        }

        try (Cursor cr = mFacade.getFavoriteAlbum(qfb.build())) {
            assertThat(cr.getCount()).isEqualTo(1);
            cr.moveToFirst();
            assertAlbumCursor(cr,
                    Category.CATEGORY_FAVORITES,
                    Category.getCategoryName(mContext, Category.CATEGORY_FAVORITES),
                    LOCAL_ID + "1",
                    DATE_TAKEN_MS,
                    /* count */ 2);
        }

        qfb.setMimeType(IMAGE_MIME_TYPE);
        try (Cursor cr = mFacade.getFavoriteAlbum(qfb.build())) {
            assertThat(cr.getCount()).isEqualTo(1);
            cr.moveToFirst();
            assertAlbumCursor(cr,
                    Category.CATEGORY_FAVORITES,
                    Category.getCategoryName(mContext, Category.CATEGORY_FAVORITES),
                    CLOUD_ID + "1",
                    DATE_TAKEN_MS,
                    /* count */ 1);
        }

        qfb.setMimeType(VIDEO_MIME_TYPE);
        try (Cursor cr = mFacade.getFavoriteAlbum(qfb.build())) {
            assertThat(cr.getCount()).isEqualTo(1);
            cr.moveToFirst();
            assertAlbumCursor(cr,
                    Category.CATEGORY_FAVORITES,
                    Category.getCategoryName(mContext, Category.CATEGORY_FAVORITES),
                    LOCAL_ID + "1",
                    DATE_TAKEN_MS,
                    /* count */ 1);
        }

        qfb.setMimeType("foo");
        try (Cursor cr = mFacade.getFavoriteAlbum(qfb.build())) {
            assertThat(cr).isNull();
        }
    }

    private Cursor queryMediaAll() {
        return mFacade.queryMedia(new PickerDbFacade.QueryFilterBuilder(1000).build());
    }

    // TODO(b/190713331): s/id/CloudMediaProviderContract#MediaColumns#ID/
    private static Cursor getDeletedMediaCursor(String id) {
        MatrixCursor c =
                new MatrixCursor(new String[] {"id"});
        c.addRow(new String[] {id});
        return c;
    }

    private static Cursor getMediaCursor(String id, long dateTakenMs, String mediaStoreUri,
            long sizeBytes, String mimeType, boolean isFavorite) {
        String[] projectionKey = new String[] {
            MediaColumns.ID,
            MediaColumns.MEDIA_STORE_URI,
            MediaColumns.DATE_TAKEN_MS,
            MediaColumns.SIZE_BYTES,
            MediaColumns.MIME_TYPE,
            MediaColumns.DURATION_MS,
            MediaColumns.IS_FAVORITE
        };

        String[] projectionValue = new String[] {
            id,
            mediaStoreUri,
            String.valueOf(dateTakenMs),
            String.valueOf(sizeBytes),
            mimeType,
            String.valueOf(DURATION_MS),
            String.valueOf(isFavorite ? 1 : 0)
        };

        MatrixCursor c = new MatrixCursor(projectionKey);
        c.addRow(projectionValue);
        return c;
    }

    private static Cursor getLocalMediaCursor(String localId, long dateTakenMs) {
        return getMediaCursor(localId, dateTakenMs, toMediaStoreUri(localId), SIZE_BYTES,
                VIDEO_MIME_TYPE, /* isFavorite */ false);
    }

    private static Cursor getCloudMediaCursor(String cloudId, String localId,
            long dateTakenMs) {
        return getMediaCursor(cloudId, dateTakenMs, toMediaStoreUri(localId), SIZE_BYTES,
                VIDEO_MIME_TYPE, /* isFavorite */ false);
    }

    private static String toMediaStoreUri(String localId) {
        if (localId == null) {
            return null;
        }
        return "content://media/external/file/" + localId;
    }

    private static void assertAlbumCursor(Cursor cursor, String albumId, String displayName,
            String mediaCoverId, long dateTakenMs, long mediaCount) {
        assertThat(cursor.getString(cursor.getColumnIndex(AlbumColumns.ID)))
                .isEqualTo(albumId);
        assertThat(cursor.getString(cursor.getColumnIndex(AlbumColumns.DISPLAY_NAME)))
                .isEqualTo(displayName);
        assertThat(cursor.getString(cursor.getColumnIndex(AlbumColumns.MEDIA_COVER_ID)))
                .isEqualTo(mediaCoverId);
        assertThat(cursor.getLong(cursor.getColumnIndex(AlbumColumns.DATE_TAKEN_MS)))
                .isEqualTo(dateTakenMs);
        assertThat(cursor.getLong(cursor.getColumnIndex(AlbumColumns.MEDIA_COUNT)))
                .isEqualTo(mediaCount);
    }

    private static void assertMediaCursor(Cursor cursor, String id) {
        final String localData = "/storage/emulated/0/.transforms/synthetic/picker/"
                + LOCAL_PROVIDER + "/media/" + id;
        final String cloudData = "/storage/emulated/0/.transforms/synthetic/picker/"
                + CLOUD_PROVIDER + "/media/" + id;

        assertThat(cursor.getString(cursor.getColumnIndex(MediaColumns.ID)))
                .isEqualTo(id);
        assertThat(cursor.getString(cursor.getColumnIndex(MediaColumns.AUTHORITY)))
                .isEqualTo(id.startsWith(LOCAL_ID) ? LOCAL_PROVIDER : CLOUD_PROVIDER);
        assertThat(cursor.getString(cursor.getColumnIndex(MediaColumns.DATA)))
                .isEqualTo(id.startsWith(LOCAL_ID) ? localData : cloudData);
    }

    private static void assertMediaCursor(Cursor cursor, String id, long dateTakenMs) {
        assertMediaCursor(cursor, id);
        assertThat(cursor.getString(cursor.getColumnIndex(MediaColumns.MIME_TYPE)))
                .isEqualTo(VIDEO_MIME_TYPE);
        assertThat(cursor.getLong(cursor.getColumnIndex(MediaColumns.DATE_TAKEN_MS)))
                .isEqualTo(dateTakenMs);
        assertThat(cursor.getLong(cursor.getColumnIndex(MediaColumns.SIZE_BYTES)))
                .isEqualTo(SIZE_BYTES);
        assertThat(cursor.getLong(cursor.getColumnIndex(MediaColumns.DURATION_MS)))
                .isEqualTo(DURATION_MS);
    }
}
