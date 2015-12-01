/*
* Copyright AllSeen Alliance. All rights reserved.
*
*    Permission to use, copy, modify, and/or distribute this software for any
*    purpose with or without fee is hereby granted, provided that the above
*    copyright notice and this permission notice appear in all copies.
*
*    THE SOFTWARE IS PROVIDED "AS IS" AND THE AUTHOR DISCLAIMS ALL WARRANTIES
*    WITH REGARD TO THIS SOFTWARE INCLUDING ALL IMPLIED WARRANTIES OF
*    MERCHANTABILITY AND FITNESS. IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR
*    ANY SPECIAL, DIRECT, INDIRECT, OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES
*    WHATSOEVER RESULTING FROM LOSS OF USE, DATA OR PROFITS, WHETHER IN AN
*    ACTION OF CONTRACT, NEGLIGENCE OR OTHER TORTIOUS ACTION, ARISING OUT OF
*    OR IN CONNECTION WITH THE USE OR PERFORMANCE OF THIS SOFTWARE.
*/
package org.alljoyn.dashboard.ui.adapter;


import java.util.ArrayList;

import org.alljoyn.dashboard.R;

import android.app.Activity;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

public abstract class AbstractSpinnerAdapter<T> extends ArrayAdapter<T>
{

    Activity m_activity = null;
    ViewGroup m_rowView = null;

    ArrayList<Boolean> m_isProtectedList;

    public AbstractSpinnerAdapter(Activity activity, int textViewResourceId)
    {
        super(activity, textViewResourceId);

        m_activity = activity;
        m_isProtectedList = new ArrayList<Boolean>();
    }

    @Override
    public View getDropDownView(int position, View convertView, ViewGroup parent) {
        final LayoutInflater inflater = (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        m_rowView = (ViewGroup) inflater.inflate(R.layout.list_item_spinner, parent, false);

        TextView name = (TextView) m_rowView.findViewById(R.id.spinner_item_text);
        name.setText(getTextAt(position));
        if (position < m_isProtectedList.size()) // make sure the data exists
        {
            name.setCompoundDrawablesWithIntrinsicBounds(0, 0, m_isProtectedList.get(position) ? R.drawable.wifi_lock_icon : 0, 0);
        }

        return m_rowView;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        final LayoutInflater inflater = (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        m_rowView = (ViewGroup) inflater.inflate(R.layout.list_item_spinner_selected, parent, false);

        TextView name = (TextView) m_rowView.findViewById(R.id.spinner_item_text);
        name.setText(getTextAt(position));
        name.setCompoundDrawablesWithIntrinsicBounds(0, 0, m_isProtectedList.get(position) ? R.drawable.wifi_lock_icon : 0, 0);

        return m_rowView;
    }

    @Override
    public void add(T t)
    {
        super.add(t);
        // assume it's not protected
        m_isProtectedList.add(false);
    }

    public void add(T t, boolean isProtected)
    {
        super.add(t);
        m_isProtectedList.add(isProtected);
    }

    @Override
    public void clear()
    {
        super.clear();
        m_isProtectedList = new ArrayList<Boolean>();
    }

    public abstract String getTextAt(int position);
}

