/*
 * Copyright (C) 2020 European Spallation Source ERIC.
 *
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */

package org.phoebus.olog.entity.preprocess;

import org.phoebus.olog.entity.Property;

/**
 * Implementations of this pre-processor interface can specify a {@link Property} that will
 * be added automatically to a log record before it is persisted. Note that addition is non-destructive, i.e. if
 * a {@link LogPropertyProvider} returns a {@link Property} already present in the submitted log entry,
 * the existing {@link Property} will not be overwritten. {@link Property}s are compared by name (case sensitive).
 */
public interface LogPropertyProvider {

    /**
     * Implementations should take care to return quickly as clients would otherwise need to wait for
     * the log submission response.
     * @return A {@link Property} that can be added to log entries.
     */
    Property getProperty();
}
