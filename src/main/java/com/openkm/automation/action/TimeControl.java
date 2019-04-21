package com.openkm.automation.action;

/*-
 * #%L
 * control-time
 * %%
 * Copyright (C) 2019 OpenKM Knowledge Management System S.L.
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * #L%
 */

import com.openkm.api.OKMDocument;
import com.openkm.api.OKMFolder;
import com.openkm.api.OKMRepository;
import com.openkm.automation.Action;
import com.openkm.automation.AutomationUtils;
import com.openkm.bean.Document;
import com.openkm.bean.Folder;
import com.openkm.core.DatabaseException;
import com.openkm.dao.ConfigDAO;
import com.openkm.dao.bean.Automation;
import com.openkm.module.db.stuff.DbSessionManager;
import com.openkm.util.PathUtils;
import net.xeoh.plugins.base.annotations.PluginImplementation;
import org.apache.commons.io.IOUtils;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.DateFormatSymbols;
import java.text.SimpleDateFormat;
import java.util.*;

@PluginImplementation
public class TimeControl implements Action {

	private String TEXT_TO_REPLACE = "<!-- nextday -->";
	private SimpleDateFormat logoutIdFormat = new SimpleDateFormat("yyyyMMdd");

	private SimpleDateFormat getDateFormat() throws DatabaseException {
		String format = ConfigDAO.getString("time.control.date.format", "dd/MM/yyyy");
		return  new SimpleDateFormat(format);
	}

	private SimpleDateFormat getHourMinuteFormat() throws DatabaseException {
		String format = ConfigDAO.getString("time.control.hour.minute.format", "HH:mm");
		return  new SimpleDateFormat(format);
	}

	/**
	 * Return the path of the control document
	 */
	private String getControlDocPath(String systemToken, Map<String, Object> env) throws Exception{
		Calendar c = Calendar.getInstance();
		String userId = AutomationUtils.getUser(env);
		String controlFolderUuid = ConfigDAO.getString("time.control.folder.uuid", "");
		Folder controlFld = OKMFolder.getInstance().getProperties(systemToken, controlFolderUuid);
		String controlDocPath = controlFld.getPath() + "/" + userId + "/" + c.get(Calendar.YEAR);

		// Create destination folder if neccesary
		OKMFolder.getInstance().createMissingFolders(systemToken, controlDocPath);
		String month = new DateFormatSymbols().getMonths()[c.get(Calendar.MONTH)];
		controlDocPath +=  "/" + month + ".html";
		return controlDocPath;
	}

	// Pre logout
	@Override
	public void executePre(Map<String, Object> env, Object... params) throws Exception  {
		InputStream is = null;
		Document document = null;

		try {
			String systemToken = DbSessionManager.getInstance().getSystemToken();
			String controlDocPath = getControlDocPath(systemToken, env);
			document = OKMDocument.getInstance().getProperties(systemToken, controlDocPath);

			String logoutId = logoutIdFormat.format(new Date());

			// Get content of the file to be able to modify it
			is = OKMDocument.getInstance().getContent(systemToken, document.getUuid(), false);
			if (is != null) {
				String body = IOUtils.toString(is, StandardCharsets.UTF_8);
				if (body.contains("<td id='logout-" + logoutId + "'>")) {
					String regexReplace = "(<td id='logout-" + logoutId + "'>(.*?)</td></tr>)";
					String newLogoutDate = "<td id='logout-" + logoutId + "'>" + getHourMinuteFormat().format(new Date()) + "</td></tr>";
					body = body.replaceAll(regexReplace, newLogoutDate);
					// Upload new document
					is.close();
					is = new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8));
					OKMDocument.getInstance().checkout(systemToken, document.getUuid());
					OKMDocument.getInstance().checkin(systemToken, document.getUuid(), is, "");
					is.close();
				}
			}

		} catch (Exception e) {
			throw new Exception("TimeControl Exception", e);
		} finally {
			IOUtils.closeQuietly(is);
		}
	}

	// Post login
	@Override
	public void executePost(Map<String, Object> env, Object... params) throws Exception {
		InputStream is = null;
		Document document = null;

		try {
			String systemToken = DbSessionManager.getInstance().getSystemToken();

			String controlDocPath = getControlDocPath(systemToken, env);
			// Check if the document exists
			if (!OKMRepository.getInstance().hasNode(systemToken, controlDocPath)) {
				String templateUuid = ConfigDAO.getString("time.control.template.uuid", "");
				String templatePath = OKMRepository.getInstance().getNodePath(systemToken, templateUuid);
				OKMDocument.getInstance().copy(systemToken, templatePath, PathUtils.getParent(controlDocPath));
				String docPath = PathUtils.getParent(controlDocPath) + "/" + PathUtils.getName(templatePath);
				String newName = PathUtils.getName(controlDocPath);
				OKMDocument.getInstance().rename(systemToken, docPath, newName);
			}
			document = OKMDocument.getInstance().getProperties(systemToken, controlDocPath);

			String today = getDateFormat().format(new Date());

			// Get content of the file to be able to modify it
			is = OKMDocument.getInstance().getContent(systemToken, document.getUuid(), false);
			boolean bFound = false;
			if (is != null) {
				String body = IOUtils.toString(is, StandardCharsets.UTF_8);
				String logoutId = logoutIdFormat.format(new Date());
				// Using logout id value to check if today has already set
				boolean todayFound = body.contains("logout-"+logoutId);

				// Actual day not found, create it, otherwise only replace at logout
				if (!todayFound) {
					if (body.contains(TEXT_TO_REPLACE)) {
						String newLine = "<tr><td>" + today + "</td><td>" + getHourMinuteFormat().format(new Date()) + "</td><td id='logout-" + logoutId + "'></td></tr>\n";
						newLine += TEXT_TO_REPLACE;
						body = body.replace(TEXT_TO_REPLACE, newLine);
						// Upload new document
						is.close();
						is = new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8));
						OKMDocument.getInstance().checkout(systemToken, document.getUuid());
						OKMDocument.getInstance().checkin(systemToken, document.getUuid(), is, "");
						is.close();
					}
				}
			}

		} catch (Exception e) {
			throw new Exception("TimeControl Exception", e);
		} finally {
			IOUtils.closeQuietly(is);
		}
	}

	@Override
	public boolean hasPost() {
		return true;
	}

	@Override
	public boolean hasPre() {
		return true;
	}

	@Override
	public String getName() {
		return "TimeControl";
	}

	@Override
	public String getParamType00() {
		return Automation.PARAM_TYPE_EMPTY;
	}

	@Override
	public String getParamSrc00() {
		return Automation.PARAM_SOURCE_EMPTY;
	}

	@Override
	public String getParamDesc00() {
		return "";
	}

	@Override
	public String getParamType01() {
		return Automation.PARAM_TYPE_EMPTY;
	}

	@Override
	public String getParamSrc01() {
		return Automation.PARAM_SOURCE_EMPTY;
	}

	@Override
	public String getParamDesc01() {
		return "";
	}

	@Override
	public String getParamType02() {
		return Automation.PARAM_TYPE_EMPTY;
	}

	@Override
	public String getParamSrc02() {
		return Automation.PARAM_SOURCE_EMPTY;
	}

	@Override
	public String getParamDesc02() {
		return "";
	}
}
