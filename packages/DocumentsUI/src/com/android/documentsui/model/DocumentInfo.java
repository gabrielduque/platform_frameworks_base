/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.documentsui.model;

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.provider.DocumentsContract.Document;

import com.android.documentsui.RecentsProvider;

import libcore.io.IoUtils;

import java.io.FileNotFoundException;
import java.util.Comparator;

/**
 * Representation of a {@link Document}.
 */
public class DocumentInfo {
    public final Uri uri;
    public final String mimeType;
    public final String displayName;
    public final long lastModified;
    public final int flags;
    public final String summary;
    public final long size;

    private DocumentInfo(Uri uri, String mimeType, String displayName, long lastModified, int flags,
            String summary, long size) {
        this.uri = uri;
        this.mimeType = mimeType;
        this.displayName = displayName;
        this.lastModified = lastModified;
        this.flags = flags;
        this.summary = summary;
        this.size = size;
    }

    public static DocumentInfo fromDirectoryCursor(Uri parent, Cursor cursor) {
        final String authority = parent.getAuthority();
        final String docId = getCursorString(cursor, Document.COLUMN_DOCUMENT_ID);

        final Uri uri = DocumentsContract.buildDocumentUri(authority, docId);
        final String mimeType = getCursorString(cursor, Document.COLUMN_MIME_TYPE);
        final String displayName = getCursorString(cursor, Document.COLUMN_DISPLAY_NAME);
        final long lastModified = getCursorLong(cursor, Document.COLUMN_LAST_MODIFIED);
        final int flags = getCursorInt(cursor, Document.COLUMN_FLAGS);
        final String summary = getCursorString(cursor, Document.COLUMN_SUMMARY);
        final long size = getCursorLong(cursor, Document.COLUMN_SIZE);

        return new DocumentInfo(uri, mimeType, displayName, lastModified, flags, summary, size);
    }

    @Deprecated
    public static DocumentInfo fromRecentOpenCursor(ContentResolver resolver, Cursor recentCursor)
            throws FileNotFoundException {
        final Uri uri = Uri.parse(getCursorString(recentCursor, RecentsProvider.COL_URI));
        final long lastModified = getCursorLong(recentCursor, RecentsProvider.COL_TIMESTAMP);

        Cursor cursor = null;
        try {
            cursor = resolver.query(uri, null, null, null, null);
            if (!cursor.moveToFirst()) {
                throw new FileNotFoundException("Missing details for " + uri);
            }
            final String mimeType = getCursorString(cursor, Document.COLUMN_MIME_TYPE);
            final String displayName = getCursorString(cursor, Document.COLUMN_DISPLAY_NAME);
            final int flags = getCursorInt(cursor, Document.COLUMN_FLAGS)
                    & Document.FLAG_SUPPORTS_THUMBNAIL;
            final String summary = getCursorString(cursor, Document.COLUMN_SUMMARY);
            final long size = getCursorLong(cursor, Document.COLUMN_SIZE);

            return new DocumentInfo(uri, mimeType, displayName, lastModified, flags, summary, size);
        } catch (Throwable t) {
            throw asFileNotFoundException(t);
        } finally {
            IoUtils.closeQuietly(cursor);
        }
    }

    public static DocumentInfo fromUri(ContentResolver resolver, Uri uri) throws FileNotFoundException {
        Cursor cursor = null;
        try {
            cursor = resolver.query(uri, null, null, null, null);
            if (!cursor.moveToFirst()) {
                throw new FileNotFoundException("Missing details for " + uri);
            }
            final String mimeType = getCursorString(cursor, Document.COLUMN_MIME_TYPE);
            final String displayName = getCursorString(cursor, Document.COLUMN_DISPLAY_NAME);
            final long lastModified = getCursorLong(cursor, Document.COLUMN_LAST_MODIFIED);
            final int flags = getCursorInt(cursor, Document.COLUMN_FLAGS);
            final String summary = getCursorString(cursor, Document.COLUMN_SUMMARY);
            final long size = getCursorLong(cursor, Document.COLUMN_SIZE);

            return new DocumentInfo(uri, mimeType, displayName, lastModified, flags, summary, size);
        } catch (Throwable t) {
            throw asFileNotFoundException(t);
        } finally {
            IoUtils.closeQuietly(cursor);
        }
    }

    @Override
    public String toString() {
        return "Document{name=" + displayName + ", uri=" + uri + "}";
    }

    public boolean isCreateSupported() {
        return (flags & Document.FLAG_DIR_SUPPORTS_CREATE) != 0;
    }

    public boolean isSearchSupported() {
        return (flags & Document.FLAG_DIR_SUPPORTS_SEARCH) != 0;
    }

    public boolean isThumbnailSupported() {
        return (flags & Document.FLAG_SUPPORTS_THUMBNAIL) != 0;
    }

    public boolean isDirectory() {
        return Document.MIME_TYPE_DIR.equals(mimeType);
    }

    public boolean isGridPreferred() {
        return (flags & Document.FLAG_DIR_PREFERS_GRID) != 0;
    }

    public boolean isDeleteSupported() {
        return (flags & Document.FLAG_SUPPORTS_DELETE) != 0;
    }

    public static String getCursorString(Cursor cursor, String columnName) {
        final int index = cursor.getColumnIndex(columnName);
        return (index != -1) ? cursor.getString(index) : null;
    }

    /**
     * Missing or null values are returned as -1.
     */
    public static long getCursorLong(Cursor cursor, String columnName) {
        final int index = cursor.getColumnIndex(columnName);
        if (index == -1) return -1;
        final String value = cursor.getString(index);
        if (value == null) return -1;
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    public static int getCursorInt(Cursor cursor, String columnName) {
        final int index = cursor.getColumnIndex(columnName);
        return (index != -1) ? cursor.getInt(index) : 0;
    }

    public static class DisplayNameComparator implements Comparator<DocumentInfo> {
        @Override
        public int compare(DocumentInfo lhs, DocumentInfo rhs) {
            final boolean leftDir = lhs.isDirectory();
            final boolean rightDir = rhs.isDirectory();

            if (leftDir != rightDir) {
                return leftDir ? -1 : 1;
            } else {
                return compareToIgnoreCaseNullable(lhs.displayName, rhs.displayName);
            }
        }
    }

    public static class LastModifiedComparator implements Comparator<DocumentInfo> {
        @Override
        public int compare(DocumentInfo lhs, DocumentInfo rhs) {
            return Long.compare(rhs.lastModified, lhs.lastModified);
        }
    }

    public static class SizeComparator implements Comparator<DocumentInfo> {
        @Override
        public int compare(DocumentInfo lhs, DocumentInfo rhs) {
            return Long.compare(rhs.size, lhs.size);
        }
    }

    public static FileNotFoundException asFileNotFoundException(Throwable t)
            throws FileNotFoundException {
        if (t instanceof FileNotFoundException) {
            throw (FileNotFoundException) t;
        }
        final FileNotFoundException fnfe = new FileNotFoundException(t.getMessage());
        fnfe.initCause(t);
        throw fnfe;
    }

    public static int compareToIgnoreCaseNullable(String lhs, String rhs) {
        if (lhs == null) return -1;
        if (rhs == null) return 1;
        return lhs.compareToIgnoreCase(rhs);
    }
}