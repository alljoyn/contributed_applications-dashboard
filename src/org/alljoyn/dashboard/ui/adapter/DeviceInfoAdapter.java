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

import java.util.List;

import org.alljoyn.about.AboutKeys;
import org.alljoyn.dashboard.R;
import org.alljoyn.dashboard.ui.activity.DeviceInfoActivity.AJDeviceInfoElement;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListAdapter;
import android.widget.TextView;

public class DeviceInfoAdapter extends ArrayAdapter<AJDeviceInfoElement> implements ListAdapter
{
    private View m_rowView = null;
    private List<AJDeviceInfoElement> m_data;
    private final Context m_context;
    private LayoutInflater m_layoutInflater;

    public DeviceInfoAdapter(Context context, int textViewResourceId) {
        super(context, textViewResourceId);
        m_context = context;
    }
    //====================================================================
    public void setData(List<AJDeviceInfoElement> data) {
        m_data = data;
    }
    //====================================================================
    public void setLayoutInflator(LayoutInflater layoutInflater){
        m_layoutInflater = layoutInflater;
    }
    //====================================================================
    @Override
    public View getView(final int position, View convertView, ViewGroup parent)
    {

        if (convertView == null) {
            m_rowView = m_layoutInflater.inflate(R.layout.list_item_device_info, parent, false);
        } else {
            m_rowView = convertView;
        }

        TextView label = (TextView) m_rowView.findViewById(R.id.device_info_item_name);
        TextView content1 = (TextView) m_rowView.findViewById(R.id.device_info_item_content_1);

        AJDeviceInfoElement element = m_data.get(position);
        String key = element.key;
        Object value = element.value;

        label.setText(key);
        content1.setText(value.toString());

        if(AboutKeys.ABOUT_SUPPORTED_LANGUAGES.equals(element.key) && element.value instanceof String[]){
            String[] languages = (String[])element.value;
            StringBuffer buff=new StringBuffer();
            for (String language: languages)
            {
                if(language.length() > 0){
                    buff.append(language).append(",");
                }
            }
            content1.setText(buff.length()>0?buff.toString().substring(0,buff.length()-1):"");
        }


        return m_rowView;
    }
    //====================================================================
    @Override
    public int getCount() {
        if (isEmpty()) {
            return 0;
        }
        return m_data.size();
    }
    //====================================================================
    @Override
    public AJDeviceInfoElement getItem(int position) {
        if (isEmpty()) {
            return null;
        }
        return m_data.get(position);
    }
    //====================================================================
    @Override
    public long getItemId(int position) {
        if (isEmpty()) {
            return -1;
        }
        return m_data.get(position).hashCode();
    }
    //====================================================================
    @Override
    public boolean isEmpty() {
        return (m_data == null || m_data.size()==0);
    }
    //====================================================================
    @Override
    public int getItemViewType(int position) {
        return 1;
    }
    //====================================================================
    @Override
    public int getViewTypeCount() {
        return 1;
    }
    //====================================================================
    @Override
    public boolean hasStableIds() {
        return false;
    }
    //====================================================================
    @Override
    public boolean areAllItemsEnabled() {
        return true;
    }
    //====================================================================
    @Override
    public boolean isEnabled(int arg0) {
        return true;
    }
}
