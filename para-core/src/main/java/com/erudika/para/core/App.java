/*
 * Copyright 2013-2020 Erudika. https://erudika.com
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
 *
 * For issues and patches go to: https://github.com/erudika
 */
package com.erudika.para.core;

import com.erudika.para.AppCreatedListener;
import com.erudika.para.AppDeletedListener;
import com.erudika.para.AppSettingAddedListener;
import com.erudika.para.AppSettingRemovedListener;
import com.erudika.para.core.utils.CoreUtils;
import com.erudika.para.core.utils.ParaObjectUtils;
import com.erudika.para.annotations.Locked;
import com.erudika.para.annotations.Stored;
import static com.erudika.para.core.App.AllowedMethods.ALL;
import static com.erudika.para.core.App.AllowedMethods.GET;
import static com.erudika.para.core.App.AllowedMethods.GUEST;
import static com.erudika.para.core.App.AllowedMethods.OWN;
import static com.erudika.para.core.App.AllowedMethods.READ;
import static com.erudika.para.core.App.AllowedMethods.READ_AND_WRITE;
import static com.erudika.para.core.App.AllowedMethods.READ_ONLY;
import static com.erudika.para.core.App.AllowedMethods.READ_WRITE;
import static com.erudika.para.core.App.AllowedMethods.WRITE;
import static com.erudika.para.core.App.AllowedMethods.WRITE_ONLY;
import static com.erudika.para.core.App.AllowedMethods.fromString;
import com.erudika.para.utils.Config;
import com.erudika.para.utils.Pager;
import com.erudika.para.utils.Utils;
import com.erudika.para.validation.Constraint;
import com.erudika.para.validation.ValidationUtils;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonValue;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.collections.bidimap.DualHashBidiMap;
import org.apache.commons.lang3.StringUtils;
import javax.validation.constraints.NotBlank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This is a representation of an application within Para.
 * <br>
 * It allows the user to create separate apps running on the same infrastructure.
 * Every {@link ParaObject} belongs to an app.
 * <br>
 * Apps can have a dedicated table or they can share the same table using prefixed keys.
 * Also, apps can have a dedicated search index or share one. These are controlled by
 * the two flags {@link #isSharingTable() } and {@link #isSharingIndex() }.
 * <br>
 * Usually when we have a multi-app environment there's a parent app (dedicated) and
 * lots of child apps (shared) that share the same index with the parent app.
 *
 * @author Alex Bogdanovski [alex@erudika.com]
 */
public class App implements ParaObject, Serializable {

	/**
	 * {@value #APP_ROLE}.
	 */
	public static final String APP_ROLE = "ROLE_APP";
	/**
	 * {@value #ALLOW_ALL}.
	 */
	public static final String ALLOW_ALL = "*";

	private static final long serialVersionUID = 1L;
	private static final String PREFIX = Utils.type(App.class).concat(Config.SEPARATOR);
	private static final Set<AppCreatedListener> CREATE_LISTENERS = new LinkedHashSet<AppCreatedListener>();
	private static final Set<AppDeletedListener> DELETE_LISTENERS = new LinkedHashSet<AppDeletedListener>();
	private static final Set<AppSettingAddedListener> ADD_SETTING_LISTENERS = new LinkedHashSet<>();
	private static final Set<AppSettingRemovedListener> REMOVE_SETTING_LISTENERS = new LinkedHashSet<>();
	private static final Logger logger = LoggerFactory.getLogger(App.class);

	@Stored @Locked @NotBlank private String id;
	@Stored @Locked private Long timestamp;
	@Stored @Locked private String type;
	@Stored @Locked private String appid;
	@Stored @Locked private String parentid;
	@Stored @Locked private String creatorid;
	@Stored private Long updated;
	@Stored private String name;
	@Stored private List<String> tags;
	@Stored private Integer votes;
	@Stored private Long version;
	@Stored private Boolean stored;
	@Stored private Boolean indexed;
	@Stored private Boolean cached;

	@Stored @Locked private boolean sharingIndex;
	@Stored @Locked private boolean sharingTable;
	@Stored @Locked private String secret;
	@Stored @Locked private Boolean readOnly;
	@Stored private Map<String, String> datatypes;
	// type -> field -> constraint -> property -> value
	@Stored private Map<String, Map<String, Map<String, Map<String, ?>>>> validationConstraints;
	// subject_id -> resource_name -> [http_methods_allowed]
	@Stored private Map<String, Map<String, List<String>>> resourcePermissions;
	@Stored private Boolean active;
	@Stored private Long deleteOn;
	@Stored private Long tokenValiditySec;

	// used to store various settings, OAuth keys, etc.
	@Stored private Map<String, Object> settings;

	/**
	 * No-args constructor.
	 */
	public App() {
		this(null);
	}

	/**
	 * Default constructor.
	 * @param id the name of the app
	 */
	public App(String id) {
		this.active = true;
		this.readOnly = false;
		setId(id);
		setName(getName());
		this.sharingIndex = !isRoot(getId());
		this.sharingTable = false;
	}

	/**
	 * Returns the correct id of this app with prefix.
	 * @param id an id like "myapp"
	 * @return the full id, e.g. "app:myapp"
	 */
	public static final String id(String id) {
		if (StringUtils.startsWith(id, PREFIX)) {
			return PREFIX.concat(Utils.noSpaces(Utils.stripAndTrim(id.replaceAll(PREFIX, ""), " "), "-"));
		} else if (id != null) {
			return PREFIX.concat(Utils.noSpaces(Utils.stripAndTrim(id, " "), "-"));
		} else {
			return null;
		}
	}

	/**
	 * Returns the identifier without the "app:" prefix.
	 * @param appid app id
	 * @return just the name of the app
	 */
	public static final String identifier(String appid) {
		return (appid != null) ? appid.replaceFirst(PREFIX, "") : "";
	}

	@Override
	public final void setId(String id) {
		this.id = id(id);
	}

	/**
	 * Adds a new setting to the map.
	 * @param name a key
	 * @param value a value
	 * @return this
	 */
	public App addSetting(String name, Object value) {
		if (!StringUtils.isBlank(name) && value != null) {
			getSettings().put(name, value);
			for (AppSettingAddedListener listener : ADD_SETTING_LISTENERS) {
				listener.onSettingAdded(this, name, value);
				logger.debug("Executed {}.onSettingAdded().", listener.getClass().getName());
			}
		}
		return this;
	}

	/**
	 * Adds all settings to map of app settings and invokes all {@link AppSettingAddedListener}s.
	 * @param settings a map settings to add
	 * @return this
	 */
	public App addAllSettings(Map<String, Object> settings) {
		// add the new settings one at a time so the add setting listeners are invoked
		if (settings != null && !settings.isEmpty()) {
			for (Map.Entry<String, Object> iter : settings.entrySet()) {
				addSetting(iter.getKey(), iter.getValue());
			}
		}
		return this;
	}

	/**
	 * Returns the value of a setting for a given key.
	 * @param name the key
	 * @return the value
	 */
	public Object getSetting(String name) {
		if (!StringUtils.isBlank(name)) {
			return getSettings().get(name);
		}
		return null;
	}

	/**
	 * Removes a setting from the map.
	 * @param name the key
	 * @return this
	 */
	public App removeSetting(String name) {
		if (!StringUtils.isBlank(name)) {
			Object result = getSettings().remove(name);
			if (result != null) {
				for (AppSettingRemovedListener listener : REMOVE_SETTING_LISTENERS) {
					listener.onSettingRemoved(this, name);
					logger.debug("Executed {}.onSettingRemoved().", listener.getClass().getName());
				}
			}
		}
		return this;
	}

	/**
	 * Clears all app settings and invokes each {@link AppSettingRemovedListener}s.
	 * @return this
	 */
	public App clearSettings() {
		// remove the old settings one at a time so the remove setting listeners are invoked
		if (settings != null && !settings.isEmpty()) {
			List<String> keysToRemove = settings.keySet().stream().collect(Collectors.toList());
			keysToRemove.stream().forEach(oldKey -> removeSetting(oldKey));
		}
		return this;
	}

	/**
	 * A map of all settings (key/values).
	 * @return a map
	 */
	@JsonIgnore
	public Map<String, Object> getSettings() {
		if (settings == null) {
			settings = new LinkedHashMap<>();
		}
		return settings;
	}

	/**
	 * Overwrites the settings map.
	 * @param settings a new map
	 */
	public void setSettings(Map<String, Object> settings) {
		this.settings = settings;
	}

	/**
	 * Returns a map of user-defined data types and their validation annotations.
	 * @return the constraints map
	 */
	public Map<String, Map<String, Map<String, Map<String, ?>>>> getValidationConstraints() {
		if (validationConstraints == null) {
			validationConstraints = new LinkedHashMap<>();
		}
		return validationConstraints;
	}

	/**
	 * Sets the validation constraints map.
	 * @param validationConstraints the constraints map
	 */
	public void setValidationConstraints(Map<String, Map<String, Map<String, Map<String, ?>>>> validationConstraints) {
		this.validationConstraints = validationConstraints;
	}

	/**
	 * Returns a map of resource permissions.
	 * @return the permissions map
	 */
	public Map<String, Map<String, List<String>>> getResourcePermissions() {
		if (resourcePermissions == null) {
			resourcePermissions = new LinkedHashMap<>();
		}
		return resourcePermissions;
	}

	/**
	 * Sets the permissions map.
	 * @param resourcePermissions permissions map
	 */
	public void setResourcePermissions(Map<String, Map<String, List<String>>> resourcePermissions) {
		this.resourcePermissions = resourcePermissions;
	}

	/**
	 * The App identifier (the id but without the prefix 'app:').
	 * The identifier may start with a whitespace character e.g. " myapp".
	 * This indicates that the app is sharing a table with other apps.
	 * This is disabled by default unless 'para.prepend_shared_appids_with_space = true'
	 * @return the identifier (appid)
	 */
	public String getAppIdentifier() {
		String pre = isSharingTable() && Config.getConfigBoolean("prepend_shared_appids_with_space", false) ? " " : "";
		return (getId() != null) ? getId().replaceFirst(PREFIX, pre) : "";
	}

	/**
	 * Returns true if this application is active (enabled).
	 * @return true if active
	 */
	public Boolean getActive() {
		return active;
	}

	/**
	 * Sets the active flag. When an app is disabled (active = false)
	 * it cannot be accessed through the API.
	 * @param active true if active
	 */
	public void setActive(Boolean active) {
		this.active = active;
	}

	/**
	 * The timestamp for when this app must be deleted.
	 * @return a timestamp
	 */
	public Long getDeleteOn() {
		return deleteOn;
	}

	/**
	 * Sets the time for deletion.
	 * @param deleteOn a timestamp
	 */
	public void setDeleteOn(Long deleteOn) {
		this.deleteOn = deleteOn;
	}

	/**
	 * The validity period for access tokens in seconds.
	 * This setting is for current app only.
	 * Always returns a default value if local setting is null.
	 * @return period in seconds
	 */
	public Long getTokenValiditySec() {
		if (tokenValiditySec == null || tokenValiditySec <= 0) {
			tokenValiditySec = (long) Config.JWT_EXPIRES_AFTER_SEC;
		}
		return tokenValiditySec;
	}

	/**
	 * Sets the access token validity period in seconds.
	 * @param tokenValiditySec seconds
	 */
	public void setTokenValiditySec(Long tokenValiditySec) {
		if (tokenValiditySec == null || tokenValiditySec <= 0) {
			this.tokenValiditySec = 0L;
		}
		this.tokenValiditySec = tokenValiditySec;
	}

	/**
	 * Returns the app's secret key.
	 * @return the secret key
	 */
	@JsonIgnore
	public String getSecret() {
		return secret;
	}

	/**
	 * Sets the secret key.
	 * @param secret a secret key
	 */
	public void setSecret(String secret) {
		this.secret = secret;
	}

	/**
	 * Gets read-only mode.
	 * @return true if app is in read-only mode
	 */
	public Boolean getReadOnly() {
		return readOnly;
	}

	/**
	 * Sets read-only mode.
	 * @param readOnly true if app is in read-only mode
	 */
	public void setReadOnly(Boolean readOnly) {
		this.readOnly = readOnly;
	}

	/**
	 * Returns a set of custom data types for this app.
	 * An app can have many custom types which describe its domain.
	 * @return a map of type names (plural form to singular)
	 */
	@SuppressWarnings("unchecked")
	public Map<String, String> getDatatypes() {
		if (datatypes == null) {
			datatypes = new DualHashBidiMap();
		}
		return datatypes;
	}

	/**
	 * Sets the data types for this app.
	 * @param datatypes a map of type names (plural form to singular)
	 */
	public void setDatatypes(Map<String, String> datatypes) {
		this.datatypes = datatypes;
	}

	/**
	 * Is this a sharing the search index with other apps.
	 * @return true if it does
	 */
	public boolean isSharingIndex() {
		return isRoot(getId()) ? false : sharingIndex;
	}

	/**
	 * Sets the sharingIndex flag.
	 * @param sharingIndex false means this app should have its own dedicated index
	 */
	public void setSharingIndex(boolean sharingIndex) {
		this.sharingIndex = sharingIndex;
	}

	/**
	 * Is this a sharing the database table with other apps.
	 * @return true if it does
	 */
	public boolean isSharingTable() {
		return sharingTable;
	}

	/**
	 * Sets the sharingTable flag.
	 * @param sharingTable false means this app should have its own dedicated table
	 */
	public void setSharingTable(boolean sharingTable) {
		this.sharingTable = sharingTable;
	}

	/**
	 * Return true if the app is the root app (the first one created).
	 * @return true if root
	 */
	@JsonIgnore
	public boolean isRootApp() {
		return StringUtils.equals(id(Config.getRootAppIdentifier()), getId());
	}
	/**
	 * Return true if the app is the root app (the first one created).
	 * @param appid an app identifier
	 * @return true if root
	 */
	public static boolean isRoot(String appid) {
		return StringUtils.equals(id(Config.getRootAppIdentifier()), id(appid));
	}

	/**
	 * Returns all validation constraints for a list of types.
	 * @param types a list of valid Para data types
	 * @return a map of validation constraints for given types
	 */
	public Map<String, Map<String, Map<String, Map<String, ?>>>> getAllValidationConstraints(String... types) {
		Map<String, Map<String, Map<String, Map<String, ?>>>> allConstr = new LinkedHashMap<>();
		if (types == null || types.length == 0) {
			types = ParaObjectUtils.getAllTypes(this).values().toArray(new String[0]);
		}
		try {
			for (String aType : types) {
				Map<String, Map<String, Map<String, ?>>> vc = new LinkedHashMap<>();
				// add all core constraints first
				if (ValidationUtils.getCoreValidationConstraints().containsKey(aType)) {
					vc.putAll(ValidationUtils.getCoreValidationConstraints().get(aType));
				}
				// also add the ones that are defined locally for this app
				Map<String, Map<String, Map<String, ?>>> appConstraints = getValidationConstraints().get(aType);
				if (appConstraints != null && !appConstraints.isEmpty()) {
					vc.putAll(appConstraints);
				}
				if (!vc.isEmpty()) {
					allConstr.put(aType, vc);
				}
			}
		} catch (Exception ex) {
			logger.error(null, ex);
		}
		return allConstr;
	}

	/**
	 * Adds a new constraint to the list of constraints for a given field and type.
	 * @param type the type
	 * @param field the field
	 * @param c the constraint
	 * @return true if successful
	 */
	public boolean addValidationConstraint(String type, String field, Constraint c) {
		if (!StringUtils.isBlank(type) && !StringUtils.isBlank(field) &&
				c != null && !c.getPayload().isEmpty() &&
				Constraint.isValidConstraintName(c.getName())) {
			Map<String, Map<String, Map<String, ?>>> fieldMap = getValidationConstraints().get(type);
			Map<String, Map<String, ?>> consMap;
			if (fieldMap != null) {
				consMap = fieldMap.get(field);
				if (consMap == null) {
					consMap = new LinkedHashMap<>();
				}
			} else {
				fieldMap = new LinkedHashMap<>();
				consMap = new LinkedHashMap<>();
			}
			consMap.put(c.getName(), c.getPayload());
			fieldMap.put(field, consMap);
			getValidationConstraints().put(type, fieldMap);
			addDatatype(Utils.singularToPlural(type), type);
			return true;
		}
		return false;
	}

	/**
	 * Removes a constraint from the map.
	 * @param type the type
	 * @param field the field
	 * @param constraintName the constraint name
	 * @return true if successful
	 */
	public boolean removeValidationConstraint(String type, String field, String constraintName) {
		if (!StringUtils.isBlank(type) && !StringUtils.isBlank(field) && constraintName != null) {
			Map<String, Map<String, Map<String, ?>>> fieldsMap = getValidationConstraints().get(type);
			if (fieldsMap != null && fieldsMap.containsKey(field)) {
				if (fieldsMap.get(field).containsKey(constraintName)) {
					fieldsMap.get(field).remove(constraintName);
				}
				if (fieldsMap.get(field).isEmpty()) {
					getValidationConstraints().get(type).remove(field);
				}
				if (getValidationConstraints().get(type).isEmpty()) {
					getValidationConstraints().remove(type);
				}
				return true;
			}
		}
		return false;
	}

	/**
	 * Returns all resource permission for a list of subjects ids.
	 * @param subjectids subject ids (user ids)
	 * @return a map of all resource permissions per subject
	 */
	public Map<String, Map<String, List<String>>> getAllResourcePermissions(String... subjectids) {
		Map<String, Map<String, List<String>>> allPermits = new LinkedHashMap<>();
		if (subjectids == null || subjectids.length == 0) {
			return getResourcePermissions();
		}
		try {
			for (String subjectid : subjectids) {
				if (subjectid != null) {
					if (getResourcePermissions().containsKey(subjectid)) {
						allPermits.put(subjectid, getResourcePermissions().get(subjectid));
					} else {
						allPermits.put(subjectid, new LinkedHashMap<>(0));
					}
					if (getResourcePermissions().containsKey(ALLOW_ALL)) {
						allPermits.put(ALLOW_ALL, getResourcePermissions().get(ALLOW_ALL));
					}
				}
			}
		} catch (Exception ex) {
			logger.error(null, ex);
		}
		return allPermits;
	}

	/**
	 * Grants a new permission for a given subject and resource.
	 * @param subjectid the subject to give permissions to
	 * @param resourcePath the resource name/type
	 * @param permission the set or HTTP methods allowed
	 * @return true if successful
	 */
	public boolean grantResourcePermission(String subjectid, String resourcePath, EnumSet<AllowedMethods> permission) {
		return grantResourcePermission(subjectid, resourcePath, permission, false);
	}

	/**
	 * Grants a new permission for a given subject and resource.
	 * @param subjectid the subject to give permissions to
	 * @param resourcePath the resource name/type
	 * @param permission the set or HTTP methods allowed
	 * @param allowGuestAccess if true - all unauthenticated requests will go through, 'false' by default.
	 * @return true if successful
	 */
	public boolean grantResourcePermission(String subjectid, String resourcePath,
			EnumSet<AllowedMethods> permission, boolean allowGuestAccess) {
		// urlDecode resource path & strip slashes at both ends
		resourcePath = Utils.urlDecode(resourcePath);
		resourcePath = StringUtils.removeEnd(resourcePath, "/");
		resourcePath = StringUtils.removeStart(resourcePath, "/");
		resourcePath = StringUtils.remove(resourcePath, ".");	// Elasticsearch 2.0+ restriction
		if (!StringUtils.isBlank(subjectid) && !StringUtils.isBlank(resourcePath) &&
				permission != null && !permission.isEmpty()) {

			allowGuestAccess = permission.remove(GUEST) || allowGuestAccess;
			EnumSet<AllowedMethods> methods = getAllowedMethodsSet(permission);
			if (!getResourcePermissions().containsKey(subjectid)) {
				Map<String, List<String>> perm = new LinkedHashMap<>();
				perm.put(resourcePath, new ArrayList<>(permission.size()));
				getResourcePermissions().put(subjectid, perm);
			}
			if (allowGuestAccess && ALLOW_ALL.equals(subjectid)) {
				methods.add(GUEST);
			}
			if (permission.contains(OWN)) {
				methods.add(OWN); // limits access to objects created by the user
			}
			List<String> perm = new ArrayList<>(methods.size());
			for (AllowedMethods allowedMethod : methods) {
				perm.add(allowedMethod.toString());
			}
			getResourcePermissions().get(subjectid).put(resourcePath, perm);
			String typ = resourcePath.split("\\/")[0];
			addDatatype(Utils.singularToPlural(typ), typ);
			return true;
		}
		return false;
	}

	private EnumSet<AllowedMethods> getAllowedMethodsSet(EnumSet<AllowedMethods> permission) {
		if (permission == null) {
			return EnumSet.copyOf(AllowedMethods.NONE);
		}
		EnumSet<AllowedMethods> methods = EnumSet.copyOf(permission);
		if (isAllowAllPermission(permission)) {
			methods = EnumSet.copyOf(READ_AND_WRITE);
		} else if (permission.contains(WRITE_ONLY)) {
			methods = EnumSet.copyOf(WRITE);
		} else if (permission.contains(READ_ONLY)) {
			methods = EnumSet.copyOf(READ);
		}
		return methods;
	}

	private boolean isAllowAllPermission(EnumSet<AllowedMethods> permission) {
		return permission != null && (permission.containsAll(ALL) || permission.contains(READ_WRITE) ||	// * || rw = *
					(permission.contains(READ_ONLY) && permission.contains(WRITE_ONLY)) ||				//   r + w = *
					(permission.contains(GET) && permission.contains(WRITE_ONLY)));						//   r + w = *
	}

	/**
	 * Revokes a permission for given subject.
	 * @param subjectid subject id
	 * @param resourcePath resource path or object type
	 * @return true if successful
	 */
	public boolean revokeResourcePermission(String subjectid, String resourcePath) {
		if (!StringUtils.isBlank(subjectid) && getResourcePermissions().containsKey(subjectid) &&
				!StringUtils.isBlank(resourcePath)) {
			// urlDecode resource path
			resourcePath = Utils.urlDecode(resourcePath);
			getResourcePermissions().get(subjectid).remove(resourcePath);
			if (getResourcePermissions().get(subjectid).isEmpty()) {
				getResourcePermissions().remove(subjectid);
			}
			return true;
		}
		return false;
	}

	/**
	 * Revokes all permissions for a subject id.
	 * @param subjectid subject id
	 * @return true if successful
	 */
	public boolean revokeAllResourcePermissions(String subjectid) {
		if (!StringUtils.isBlank(subjectid) && getResourcePermissions().containsKey(subjectid)) {
			getResourcePermissions().remove(subjectid);
			return true;
		}
		return false;
	}

	/**
	 * Checks if a subject is allowed to call method X on resource Y.
	 * @param subjectid subject id
	 * @param resourcePath resource path or object type
	 * @param httpMethod HTTP method name
	 * @return true if allowed
	 */
	public boolean isAllowedTo(String subjectid, String resourcePath, String httpMethod) {
		boolean allow = false;
		if (subjectid != null && !StringUtils.isBlank(resourcePath) && !StringUtils.isBlank(httpMethod)) {
			// urlDecode resource path
			resourcePath = Utils.urlDecode(resourcePath);

			if (getResourcePermissions().isEmpty()) {
				// Default policy is "deny all". Returning true here would make it "allow all".
				return false;
			}
			if (isDeniedExplicitly(subjectid, resourcePath, httpMethod)) {
				return false;
			}
			if (getResourcePermissions().containsKey(subjectid) &&
					getResourcePermissions().get(subjectid).containsKey(resourcePath)) {
				// subject-specific permissions have precedence over wildcard permissions
				// i.e. only the permissions for that subjectid are checked, other permissions are ignored
				allow = isAllowed(subjectid, resourcePath, httpMethod);
			} else {
				allow = isAllowed(subjectid, resourcePath, httpMethod) ||
						isAllowed(subjectid, ALLOW_ALL, httpMethod) ||
						isAllowed(ALLOW_ALL, resourcePath, httpMethod) ||
						isAllowed(ALLOW_ALL, ALLOW_ALL, httpMethod);
			}
		}
		if (allow) {
			if (isRootApp() && !Config.getConfigBoolean("clients_can_access_root_app", false)) {
				return false;
			}
			if (StringUtils.isBlank(subjectid)) {
				// guest access check
				return isAllowed(ALLOW_ALL, resourcePath, GUEST.toString());
			}
			return true;
		}
		return isAllowedImplicitly(subjectid, resourcePath, httpMethod);
	}

	final boolean isAllowed(String subjectid, String resourcePath, String httpMethod) {
		boolean allowed = false;
		if (subjectid != null && resourcePath != null && getResourcePermissions().containsKey(subjectid)) {
			httpMethod = StringUtils.upperCase(httpMethod);
			String wildcard = ALLOW_ALL;
			String exactPathToMatch = resourcePath;
			if (fromString(httpMethod) == GUEST) {
				// special case where we have wildcard permissions * but public access is not allowed
				wildcard = httpMethod;
			}
			if (StringUtils.contains(resourcePath, '/')) {
				// we assume that a full resource path is given like: 'users/something/123'
				// so we check to see if 'users/something' is in the list of resources.
				// we don't want 'users/someth' to match, but only the exact full path
				String fragment = resourcePath.substring(0, resourcePath.lastIndexOf('/'));
				for (String resource : getResourcePermissions().get(subjectid).keySet()) {
					if (StringUtils.startsWith(fragment, resource) &&
							pathMatches(subjectid, resource, httpMethod, wildcard)) {
						allowed = true;
						break;
					}
					// allow basic wildcard matching
					if (StringUtils.endsWith(resource, "/*") &&
							resourcePath.startsWith(resource.substring(0, resource.length() - 1))) {
						exactPathToMatch = resource;
						break;
					}
				}
			}
			if (!allowed && getResourcePermissions().get(subjectid).containsKey(exactPathToMatch)) {
				// check if exact resource path is accessible
				allowed = pathMatches(subjectid, exactPathToMatch, httpMethod, wildcard);
			} else if (!allowed && getResourcePermissions().get(subjectid).containsKey(ALLOW_ALL)) {
				// check if ALL resources are accessible
				allowed = pathMatches(subjectid, ALLOW_ALL, httpMethod, wildcard);
			}
		}
		return allowed;
	}


	private boolean pathMatches(String subjectid, String path, String httpMethod, String wildcard) {
		return (getResourcePermissions().getOrDefault(subjectid, Collections.emptyMap()).
				getOrDefault(path, Collections.emptyList()).contains(httpMethod)
						|| getResourcePermissions().getOrDefault(subjectid, Collections.emptyMap()).
								getOrDefault(path, Collections.emptyList()).contains(wildcard));
	}

	/**
	 * Check if a subject is explicitly denied access to a resource.
	 * @param subjectid subject id
	 * @param resourcePath resource path or object type
	 * @param httpMethod HTTP method name
	 * @return true if access is explicitly denied
	 */
	final boolean isDeniedExplicitly(String subjectid, String resourcePath, String httpMethod) {
		if (StringUtils.isBlank(subjectid) || StringUtils.isBlank(resourcePath) ||
				StringUtils.isBlank(httpMethod) || getResourcePermissions().isEmpty()) {
			return false;
		}
		// urlDecode resource path
		resourcePath = Utils.urlDecode(resourcePath);
		if (getResourcePermissions().containsKey(subjectid)) {
			if (getResourcePermissions().get(subjectid).containsKey(resourcePath)) {
				return !isAllowed(subjectid, resourcePath, httpMethod);
			} else if (getResourcePermissions().get(subjectid).containsKey(ALLOW_ALL)) {
				return !isAllowed(subjectid, ALLOW_ALL, httpMethod);
			}
		}
		return false;
	}

	/**
	 * Check if a request comes from a signed in user who try to read/update themselves.
	 * @param subjectid subject id
	 * @param resourcePath resource path or object type
	 * @param httpMethod HTTP method name
	 * @return true if request is GET, PATCH or PUT and the subject id matches the object id
	 */
	final boolean isAllowedImplicitly(String subjectid, String resourcePath, String httpMethod) {
		if (StringUtils.isBlank(subjectid) || StringUtils.isBlank(resourcePath) || StringUtils.isBlank(httpMethod)) {
			return false;
		}
		if (resourcePath.endsWith("/" + subjectid)) {
			// implicit permissions: a user can read/update/delete itself
			return (httpMethod.equals("GET") || httpMethod.equals("PATCH") || httpMethod.equals("DELETE"));
		}
		return false;
	}

	/**
	 * Check if the permissions map contains "OWN" keyword, which restricts access to objects to their creators.
	 *
	 * @param user user in context
	 * @param object some object
	 * @return true if app contains permission for this resource and it is marked with "OWN"
	 */
	public boolean permissionsContainOwnKeyword(User user, ParaObject object) {
		if (user == null || object == null) {
			return false;
		}
		String resourcePath1 = object.getType();
		String resourcePath2 = object.getObjectURI().substring(1); // remove first '/'
		String resourcePath3 = object.getPlural();
		return hasOwnKeyword(App.ALLOW_ALL, resourcePath1)
				|| hasOwnKeyword(App.ALLOW_ALL, resourcePath2)
				|| hasOwnKeyword(App.ALLOW_ALL, resourcePath3)
				|| hasOwnKeyword(user.getId(), resourcePath1)
				|| hasOwnKeyword(user.getId(), resourcePath2)
				|| hasOwnKeyword(user.getId(), resourcePath3);
	}

	/**
	 * @param subjectid id of user
	 * @param resourcePath path
	 * @return true if app contains permission for this resource path and it is marked with "OWN"
	 */
	final boolean hasOwnKeyword(String subjectid, String resourcePath) {
		if (subjectid == null || resourcePath == null) {
			return false;
		}
		return getResourcePermissions().containsKey(subjectid)
				&& getResourcePermissions().get(subjectid).containsKey(resourcePath)
				&& getResourcePermissions().
						get(subjectid).
						get(resourcePath).
						contains(App.AllowedMethods.OWN.toString());
	}

	/**
	 * Adds a user-defined data type to the types map.
	 * @param pluralDatatype the plural form of the type
	 * @param datatype a datatype, must not be null or empty
	 */
	public void addDatatype(String pluralDatatype, String datatype) {
		pluralDatatype = Utils.noSpaces(Utils.stripAndTrim(pluralDatatype, " "), "-");
		datatype = Utils.noSpaces(Utils.stripAndTrim(datatype, " "), "-");
		if (StringUtils.isBlank(pluralDatatype) || StringUtils.isBlank(datatype)) {
			return;
		}
		if (getDatatypes().size() >= Config.MAX_DATATYPES_PER_APP) {
			LoggerFactory.getLogger(App.class).warn("Maximum number of types per app reached - {}.",
					Config.MAX_DATATYPES_PER_APP);
			return;
		}
		if (!getDatatypes().containsKey(pluralDatatype) && !getDatatypes().containsValue(datatype) &&
					!ParaObjectUtils.getCoreTypes().containsKey(pluralDatatype)) {
			getDatatypes().put(pluralDatatype, datatype);
		}
	}

	/**
	 * Adds unknown types to this app's list of data types. Called on create().
	 * @param objects a list of new objects
	 */
	public void addDatatypes(ParaObject... objects) {
		// register a new data type
		if (objects != null && objects.length > 0) {
			for (ParaObject obj : objects) {
				if (obj != null && obj.getType() != null) {
					addDatatype(obj.getPlural(), obj.getType());
				}
			}
		}
	}

	/**
	 * Removes a datatype from the types map.
	 * @param pluralDatatype a datatype, must not be null or empty
	 */
	public void removeDatatype(String pluralDatatype) {
		if (!StringUtils.isBlank(pluralDatatype)) {
			getDatatypes().remove(pluralDatatype);
		}
	}

	/**
	 * Resets the secret key by generating a new one.
	 */
	public void resetSecret() {
		secret = Utils.generateSecurityToken(40);
	}

	/**
	 * Returns the map containing the app's access key and secret key.
	 * @return a map of API keys (never null)
	 */
	@JsonIgnore
	public Map<String, String> getCredentials() {
		if (getId() == null) {
			return Collections.emptyMap();
		} else {
			Map<String, String> keys = new LinkedHashMap<String, String>(2);
			keys.put("accessKey", getId());
			keys.put("secretKey", getSecret());
			return keys;
		}
	}

	/**
	 * Registers a new create listener.
	 * @param listener the listener
	 */
	public static void addAppCreatedListener(AppCreatedListener listener) {
		if (listener != null) {
			CREATE_LISTENERS.add(listener);
		}
	}

	/**
	 * Registers a new delete listener.
	 * @param listener the listener
	 */
	public static void addAppDeletedListener(AppDeletedListener listener) {
		if (listener != null) {
			DELETE_LISTENERS.add(listener);
		}
	}

	/**
	 * Registers a new app setting added listener.
	 * @param listener the listener
	 */
	public static void addAppSettingAddedListener(AppSettingAddedListener listener) {
		if (listener != null) {
			ADD_SETTING_LISTENERS.add(listener);
		}
	}

	/**
	 * Registers a new app setting removed listener.
	 * @param listener the listener
	 */
	public static void addAppSettingRemovedListener(AppSettingRemovedListener listener) {
		if (listener != null) {
			REMOVE_SETTING_LISTENERS.add(listener);
		}
	}

	@Override
	public String create() {
		if (getId() != null && this.exists()) {
			return null;
		}
		if (!isRoot(getAppid())) {
			// third level apps not allowed
			logger.error("Child apps cannot contain app objects.");
			return null;
		}
		if (StringUtils.isBlank(secret)) {
			resetSecret();
		}
		String appId = CoreUtils.getInstance().getDao().create(getAppid(), this);
		if (!isRootApp()) {
			for (AppCreatedListener listener : CREATE_LISTENERS) {
				listener.onAppCreated(this);
				logger.debug("Executed {}.onAppCreated().", listener.getClass().getName());
			}
		}
		return appId;
	}

	@Override
	public void delete() {
		// root app cannot be deleted
		if (!isRootApp()) {
			CoreUtils.getInstance().getDao().delete(getAppid(), this);
			logger.info("App '{}' deleted.", getId());
			for (AppDeletedListener listener : DELETE_LISTENERS) {
				listener.onAppDeleted(this);
				logger.info("Executed {}.onAppDeleted().", listener.getClass().getName());
			}
			clearSettings();
		}
	}

	////////////////////////////////////////////////////////

	@Override
	public final String getId() {
		return id;
	}

	@Override
	public final String getType() {
		type = (type == null) ? Utils.type(this.getClass()) : type;
		return type;
	}

	@Override
	public final void setType(String type) {
		this.type = type;
	}

	@Override
	public String getAppid() {
		appid = (appid == null) ? Config.getRootAppIdentifier() : appid;
		return appid;
	}

	@Override
	public void setAppid(String appid) {
		this.appid = appid;
	}

	@Override
	public String getObjectURI() {
		return CoreUtils.getInstance().getObjectURI(this);
	}

	@Override
	public List<String> getTags() {
		return tags;
	}

	@Override
	public void setTags(List<String> tags) {
		this.tags = tags;
	}

	@Override
	public Boolean getStored() {
		if (stored == null) {
			stored = true;
		}
		return stored;
	}

	@Override
	public void setStored(Boolean stored) {
		this.stored = stored;
	}

	@Override
	public Boolean getIndexed() {
		if (indexed == null) {
			indexed = true;
		}
		return indexed;
	}

	@Override
	public void setIndexed(Boolean indexed) {
		this.indexed = indexed;
	}

	@Override
	public Boolean getCached() {
		if (cached == null) {
			cached = true;
		}
		return cached;
	}

	@Override
	public void setCached(Boolean cached) {
		this.cached = cached;
	}

	@Override
	public Long getTimestamp() {
		return (timestamp != null && timestamp != 0) ? timestamp : null;
	}

	@Override
	public void setTimestamp(Long timestamp) {
		this.timestamp = timestamp;
	}

	@Override
	public String getCreatorid() {
		return creatorid;
	}

	@Override
	public void setCreatorid(String creatorid) {
		this.creatorid = creatorid;
	}

	@Override
	public final String getName() {
		return CoreUtils.getInstance().getName(name, id);
	}

	@Override
	public final void setName(String name) {
		this.name = (name == null || !name.isEmpty()) ? name : this.name;
	}

	@Override
	public String getPlural() {
		return Utils.singularToPlural(getType());
	}

	@Override
	public String getParentid() {
		return parentid;
	}

	@Override
	public void setParentid(String parentid) {
		this.parentid = parentid;
	}

	@Override
	public Long getUpdated() {
		return (updated != null && updated != 0) ? updated : null;
	}

	@Override
	public void setUpdated(Long updated) {
		this.updated = updated;
	}

	@Override
	public void update() {
		CoreUtils.getInstance().getDao().update(getAppid(), this);
	}

	@Override
	public boolean exists() {
		return CoreUtils.getInstance().getDao().read(getAppid(), getId()) != null;
	}

	@Override
	public boolean voteUp(String userid) {
		return CoreUtils.getInstance().vote(this, userid, VoteValue.UP);
	}

	@Override
	public boolean voteDown(String userid) {
		return CoreUtils.getInstance().vote(this, userid, VoteValue.DOWN);
	}

	@Override
	public Integer getVotes() {
		return (votes == null) ? 0 : votes;
	}

	@Override
	public void setVotes(Integer votes) {
		this.votes = votes;
	}

	@Override
	public Long getVersion() {
		return (version == null) ? 0 : version;
	}

	@Override
	public void setVersion(Long version) {
		this.version = version;
	}

	@Override
	public Long countLinks(String type2) {
		return CoreUtils.getInstance().countLinks(this, type2);
	}

	@Override
	public List<Linker> getLinks(String type2, Pager... pager) {
		return CoreUtils.getInstance().getLinks(this, type2, pager);
	}

	@Override
	public <P extends ParaObject> List<P> getLinkedObjects(String type, Pager... pager) {
		return CoreUtils.getInstance().getLinkedObjects(this, type, pager);
	}

	@Override
	public <P extends ParaObject> List<P> findLinkedObjects(String type, String field, String query, Pager... pager) {
		return CoreUtils.getInstance().findLinkedObjects(this, type, field, query, pager);
	}

	@Override
	public boolean isLinked(String type2, String id2) {
		return CoreUtils.getInstance().isLinked(this, type2, id2);
	}

	@Override
	public boolean isLinked(ParaObject toObj) {
		return CoreUtils.getInstance().isLinked(this, toObj);
	}

	@Override
	public String link(String id2) {
		return CoreUtils.getInstance().link(this, id2);
	}

	@Override
	public void unlink(String type, String id2) {
		CoreUtils.getInstance().unlink(this, type, id2);
	}

	@Override
	public void unlinkAll() {
		CoreUtils.getInstance().unlinkAll(this);
	}

	@Override
	public Long countChildren(String type) {
		return CoreUtils.getInstance().countChildren(this, type);
	}

	@Override
	public <P extends ParaObject> List<P> getChildren(String type, Pager... pager) {
		return CoreUtils.getInstance().getChildren(this, type, pager);
	}

	@Override
	public <P extends ParaObject> List<P> getChildren(String type, String field, String term, Pager... pager) {
		return CoreUtils.getInstance().getChildren(this, type, field, term, pager);
	}

	@Override
	public <P extends ParaObject> List<P> findChildren(String type, String query, Pager... pager) {
		return CoreUtils.getInstance().findChildren(this, type, query, pager);
	}

	@Override
	public void deleteChildren(String type) {
		CoreUtils.getInstance().deleteChildren(this, type);
	}

	@Override
	public int hashCode() {
		int hash = 7;
		hash = 67 * hash + Objects.hashCode(this.id) + Objects.hashCode(this.name);
		return hash;
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == null) {
			return false;
		}
		if (getClass() != obj.getClass()) {
			return false;
		}
		final ParaObject other = (ParaObject) obj;
		return Objects.equals(this.id, other.getId());
	}

	@Override
	public String toString() {
		return ParaObjectUtils.toJSON(this);
	}

	/**
	 * Represents HTTP methods allowed to be executed on a specific resource/type.
	 * For example; the 'books' type can have a permission '{ "*" : ["GET"] }' which means
	 * "give read-only permissions to everyone". It is backed by a map of resource names
	 * (object types) to a set of allowed HTTP methods.
	 */
	public enum AllowedMethods {

		/**
		 * Allow unauthenticated requests (guest access).
		 */
		GUEST,
		/**
		 * Deny all requests (no access).
		 */
		EMPTY,
		/**
		 * Restrict access to objects with creatorid matching that of auth user.
		 */
		OWN,
		/**
		 * Allows all HTTP methods (full access).
		 */
		READ_WRITE,
		/**
		 * Allows GET method only.
		 */
		GET,
		/**
		 * Allows POST method only.
		 */
		POST,
		/**
		 * Allows PUT method only.
		 */
		PUT,
		/**
		 * ALlows PATCH method only.
		 */
		PATCH,
		/**
		 * Allows DELETE method only.
		 */
		DELETE,
		/**
		 * Allows read methods: GET, same as {@link #GET}.
		 */
		READ_ONLY,
		/**
		 * Allows write methods: POST, PUT, PATCH and DELETE.
		 */
		WRITE_ONLY;

		/**
		 * All methods allowed.
		 */
		public static final EnumSet<AllowedMethods> ALL = EnumSet.of(GET, POST, PUT, PATCH, DELETE);
		/**
		 * All methods allowed (*).
		 */
		public static final EnumSet<AllowedMethods> READ_AND_WRITE = EnumSet.of(READ_WRITE);
		/**
		 * Only GET is allowed.
		 */
		public static final EnumSet<AllowedMethods> READ = EnumSet.of(GET);
		/**
		 * All methods allowed, except GET.
		 */
		public static final EnumSet<AllowedMethods> WRITE = EnumSet.of(POST, PUT, PATCH, DELETE);
		/**
		 * All methods allowed, except DELETE.
		 */
		public static final EnumSet<AllowedMethods> ALL_EXCEPT_DELETE = EnumSet.of(GET, POST, PUT, PATCH);
		/**
		 * No methods allowed.
		 */
		public static final EnumSet<AllowedMethods> NONE = EnumSet.of(EMPTY);

		/**
		 * Constructs the enum from a string value.
		 * @param value a method name, or ?,w
		 * @return an enum instance
		 */
		@JsonCreator
		public static AllowedMethods fromString(String value) {
			if (ALLOW_ALL.equals(value)) {
				return READ_WRITE;
			} else if ("w".equals(value)) {
				return WRITE_ONLY;
			} else if ("?".equals(value)) {
				return GUEST;
			} else {
				try {
					return valueOf(value.toUpperCase());
				} catch (Exception e) {
					return EMPTY;
				}
			}
		}

		@Override
		@JsonValue
		public String toString() {
			switch (this) {
				case READ_WRITE:
					return ALLOW_ALL;
				case READ_ONLY:
					return GET.name();
				case GUEST:
					return "?";
				case EMPTY:
					return "-";
				case WRITE_ONLY:
					return "w";
				default:
					return this.name();
			}
		}
	}
}
