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
package org.alljoyn.dashboard.ui.wrappers;

import org.alljoyn.onboarding.OnboardingService.AuthType;

/**
 * A wrapper class for hosting a AuthType inside an ArrayAdapter. So that the
 * label is displayed, while practically the real value is used.
 */
public class AuthTypeWrapper {
    public final AuthType value;

    public AuthTypeWrapper(AuthType value) {
        super();
        this.value = value;
    }

    @Override
    // This does the trick of displaying the label and not the value in the
    // Adapter
    public String toString() {
        if (AuthType.WPA_AUTO.equals(value)
                || AuthType.WPA_CCMP.equals(value)
                || AuthType.WPA_TKIP.equals(value)) {
            return "WPA";
        } else if (AuthType.WPA2_AUTO.equals(value)
                || AuthType.WPA2_CCMP.equals(value)
                || AuthType.WPA2_TKIP.equals(value)) {
            return "WPA2";
        } else if (AuthType.OPEN.equals(value)) {
            return "None";
        }
        return value.toString();
    }
}
