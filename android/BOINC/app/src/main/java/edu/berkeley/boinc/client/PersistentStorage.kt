/*
 * This file is part of BOINC.
 * http://boinc.berkeley.edu
 * Copyright (C) 2020 University of California
 *
 * BOINC is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * BOINC is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with BOINC.  If not, see <http://www.gnu.org/licenses/>.
 */
package edu.berkeley.boinc.client

import android.content.Context
import android.content.SharedPreferences

/**
 * This class wraps persistent key value pairs.
 * Similar technique to AppPrefs, but with a non-preference incentive.
 */
class PersistentStorage(ctx: Context) {
    private val store: SharedPreferences = ctx.getSharedPreferences("Store", 0)

    var lastNotifiedNoticeArrivalTime: Double
        get() {
            val defaultValue = 0L
            return Double.fromBits(store.getLong("lastNotifiedNoticeArrivalTime",
                    defaultValue.toDouble().toRawBits()))
        }
        set(arrivalTime) {
            val editor = store.edit()
            editor.putLong("lastNotifiedNoticeArrivalTime", arrivalTime.toRawBits())
            editor.apply()
        }

    var lastEmailAddress: String?
        get() = store.getString("lastEmailAddress", "")
        set(email) {
            val editor = store.edit()
            editor.putString("lastEmailAddress", email)
            editor.apply()
        }

    var lastUserName: String?
        get() = store.getString("lastUserName", "")
        set(name) {
            val editor = store.edit()
            editor.putString("lastUserName", name)
            editor.apply()
        }
}
