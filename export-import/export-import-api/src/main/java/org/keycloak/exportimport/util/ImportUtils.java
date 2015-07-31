package org.keycloak.exportimport.util;

import org.codehaus.jackson.JsonFactory;
import org.codehaus.jackson.JsonParser;
import org.codehaus.jackson.JsonToken;
import org.codehaus.jackson.map.ObjectMapper;
import org.jboss.logging.Logger;
import org.keycloak.Config;
import org.keycloak.exportimport.Strategy;
import org.keycloak.models.AdminRoles;
import org.keycloak.models.ClientModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.RealmProvider;
import org.keycloak.models.RoleModel;
import org.keycloak.models.utils.KeycloakModelUtils;
import org.keycloak.models.utils.RepresentationToModel;
import org.keycloak.representations.idm.RealmRepresentation;
import org.keycloak.representations.idm.UserRepresentation;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;

import org.keycloak.exportimport.ExportImportConfig;

/**
 * @author <a href="mailto:mposolda@redhat.com">Marek Posolda</a>
 */
public class ImportUtils {

    private static final Logger logger = Logger.getLogger(ImportUtils.class);

    public static void importRealms(KeycloakSession session, Collection<RealmRepresentation> realms, Strategy strategy) {
        // Import admin realm first
        for (RealmRepresentation realm : realms) {
            if (Config.getAdminRealm().equals(realm.getRealm())) {
                importRealm(session, realm, strategy);
            }
        }

        for (RealmRepresentation realm : realms) {
            if (!Config.getAdminRealm().equals(realm.getRealm())) {
                importRealm(session, realm, strategy);
            }
        }
    }

    /**
     * Fully import realm from representation, save it to model and return model of newly created realm
     *
     * @param session
     * @param rep
     * @param strategy specifies whether to overwrite or ignore existing realm or user entries
     * @return newly imported realm (or existing realm if ignoreExisting is true and realm of this name already exists)
     */
    public static void importRealm(KeycloakSession session, RealmRepresentation rep, Strategy strategy) {
        String realmName = rep.getRealm();
        RealmProvider model = session.realms();
        RealmModel realm = model.getRealmByName(realmName);

        if (realm != null) {
            if (strategy == Strategy.IGNORE_EXISTING) {
                logger.infof("Realm '%s' already exists. Import skipped", realmName);
                return;
            } else {
                logger.infof("Realm '%s' already exists. Removing it before import", realmName);
                if (Config.getAdminRealm().equals(realm.getId())) {
                    // Delete all masterAdmin apps due to foreign key constraints
                    for (RealmModel currRealm : model.getRealms()) {
                        currRealm.setMasterAdminClient(null);
                    }
                }
                // TODO: For migration between versions, it should be possible to delete just realm but keep it's users
                model.removeRealm(realm.getId());
            }
        }

        realm = rep.getId() != null ? model.createRealm(rep.getId(), realmName) : model.createRealm(realmName);

        RepresentationToModel.importRealm(session, rep, realm);

        refreshMasterAdminApps(model, realm);

        if (System.getProperty(ExportImportConfig.ACTION) != null) {
            logger.infof("Realm '%s' imported", realmName);
        }
        
        return;
    }

    private static void refreshMasterAdminApps(RealmProvider model, RealmModel realm) {
        String adminRealmId = Config.getAdminRealm();
        if (adminRealmId.equals(realm.getId())) {
            // We just imported master realm. All 'masterAdminApps' need to be refreshed
            RealmModel adminRealm = realm;
            for (RealmModel currentRealm : model.getRealms()) {
                ClientModel masterApp = adminRealm.getClientByClientId(KeycloakModelUtils.getMasterRealmAdminApplicationClientId(currentRealm));
                if (masterApp != null) {
                    currentRealm.setMasterAdminClient(masterApp);
                }  else {
                    setupMasterAdminManagement(model, currentRealm);
                }
            }
        } else {
            // Need to refresh masterApp for current realm
            RealmModel adminRealm = model.getRealm(adminRealmId);
            ClientModel masterApp = adminRealm.getClientByClientId(KeycloakModelUtils.getMasterRealmAdminApplicationClientId(realm));
            if (masterApp != null) {
                realm.setMasterAdminClient(masterApp);
            }  else {
                setupMasterAdminManagement(model, realm);
            }
        }
    }

    // TODO: We need method here, so we are able to refresh masterAdmin applications after import. Should be RealmManager moved to model/api instead?
    public static void setupMasterAdminManagement(RealmProvider model, RealmModel realm) {
        RealmModel adminRealm;
        RoleModel adminRole;

        if (realm.getName().equals(Config.getAdminRealm())) {
            adminRealm = realm;

            adminRole = realm.addRole(AdminRoles.ADMIN);

            RoleModel createRealmRole = realm.addRole(AdminRoles.CREATE_REALM);
            adminRole.addCompositeRole(createRealmRole);
            createRealmRole.setDescription("${role_"+AdminRoles.CREATE_REALM+"}");
        } else {
            adminRealm = model.getRealmByName(Config.getAdminRealm());
            adminRole = adminRealm.getRole(AdminRoles.ADMIN);
        }
        adminRole.setDescription("${role_"+AdminRoles.ADMIN+"}");

        ClientModel realmAdminApp = KeycloakModelUtils.createClient(adminRealm, KeycloakModelUtils.getMasterRealmAdminApplicationClientId(realm));
        // No localized name for now
        realmAdminApp.setName(realm.getName() + " Realm");
        realmAdminApp.setBearerOnly(true);
        realm.setMasterAdminClient(realmAdminApp);

        for (String r : AdminRoles.ALL_REALM_ROLES) {
            RoleModel role = realmAdminApp.addRole(r);
            role.setDescription("${role_"+r+"}");
            adminRole.addCompositeRole(role);
        }
    }


    /**
     * Fully import realm (or more realms from particular stream)
     *
     * @param session
     * @param mapper
     * @param is
     * @param strategy
     * @throws IOException
     */
    public static void importFromStream(KeycloakSession session, ObjectMapper mapper, InputStream is, Strategy strategy) throws IOException {
        Map<String, RealmRepresentation> realmReps = getRealmsFromStream(mapper, is);
        importRealms(session, realmReps.values(), strategy);
    }

    public static Map<String, RealmRepresentation> getRealmsFromStream(ObjectMapper mapper, InputStream is) throws IOException {
        Map<String, RealmRepresentation> result = new HashMap<String, RealmRepresentation>();

        JsonFactory factory = mapper.getJsonFactory();
        JsonParser parser = factory.createJsonParser(is);
        try {
            parser.nextToken();

            if (parser.getCurrentToken() == JsonToken.START_ARRAY) {
                // Case with more realms in stream
                parser.nextToken();

                List<RealmRepresentation> realmReps = new ArrayList<RealmRepresentation>();
                while (parser.getCurrentToken() == JsonToken.START_OBJECT) {
                    RealmRepresentation realmRep = parser.readValueAs(RealmRepresentation.class);
                    parser.nextToken();

                    // Ensure that master realm is imported first
                    if (Config.getAdminRealm().equals(realmRep.getRealm())) {
                        realmReps.add(0, realmRep);
                    } else {
                        realmReps.add(realmRep);
                    }
                }

                for (RealmRepresentation realmRep : realmReps) {
                    result.put(realmRep.getId(), realmRep);
                }
            } else if (parser.getCurrentToken() == JsonToken.START_OBJECT) {
                // Case with single realm in stream
                RealmRepresentation realmRep = parser.readValueAs(RealmRepresentation.class);
                result.put(realmRep.getId(), realmRep);
            }
        } finally {
            parser.close();
        }

        return result;
    }


    // Assuming that it's invoked inside transaction
    public static void importUsersFromStream(KeycloakSession session, String realmName, ObjectMapper mapper, InputStream is) throws IOException {
        RealmProvider model = session.realms();
        JsonFactory factory = mapper.getJsonFactory();
        JsonParser parser = factory.createJsonParser(is);
        try {
            parser.nextToken();

            while (parser.nextToken() == JsonToken.FIELD_NAME) {
                if ("realm".equals(parser.getText())) {
                    parser.nextToken();
                    String currRealmName = parser.getText();
                    if (!currRealmName.equals(realmName)) {
                        throw new IllegalStateException("Trying to import users into invalid realm. Realm name: " + realmName + ", Expected realm name: " + currRealmName);
                    }
                } else if ("users".equals(parser.getText())) {
                    parser.nextToken();

                    if (parser.getCurrentToken() == JsonToken.START_ARRAY) {
                        parser.nextToken();
                    }

                    // TODO: support for more transactions per single users file (if needed)
                    List<UserRepresentation> userReps = new ArrayList<UserRepresentation>();
                    while (parser.getCurrentToken() == JsonToken.START_OBJECT) {
                        UserRepresentation user = parser.readValueAs(UserRepresentation.class);
                        userReps.add(user);
                        parser.nextToken();
                    }

                    importUsers(session, model, realmName, userReps);

                    if (parser.getCurrentToken() == JsonToken.END_ARRAY) {
                        parser.nextToken();
                    }
                }
            }
        } finally {
            parser.close();
        }
    }

    private static void importUsers(KeycloakSession session, RealmProvider model, String realmName, List<UserRepresentation> userReps) {
        RealmModel realm = model.getRealmByName(realmName);
        Map<String, ClientModel> apps = realm.getClientNameMap();
        for (UserRepresentation user : userReps) {
            RepresentationToModel.createUser(session, realm, user, apps);
        }
    }

}
