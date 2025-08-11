# À propos de l'application Support

* Licence : [AGPL v3](http://www.gnu.org/licenses/agpl.txt) - Copyright Région Hauts-de-France (ex Picardie), Département de l'Essonne, Région Nouvelle Aquitaine (ex Poitou-Charentes), Edifice
* Développeur(s) : ATOS, Edifice, CGI
* Financeur(s) : Région Hauts-de-France (ex Picardie), Département de l'Essonne, Région Nouvelle Aquitaine (ex Poitou-Charentes), Edifice
* Description : Application de gestion de tickets support internes à l'ENT avec gestion de l'escalade vers un service tiers comme Redmine ou Zendesk. L'application permet à un utilisateur d'ouvrir et de suivre un ticket de support sur l'ENT. Un gestionnaire peut prendre en charge le ticket de support, le traiter ou l'escalader au support de niveau 2/3 directement dans l'ENT. Le ticket est alors transféré vers un service tiers automatiquement.

## Déployer dans ent-core
<pre>
		gradle clean install
</pre>

# Présentation du module

## Fonctionnalités

Aide & Support permet de signaler une difficulté ou un problème d'utilisation de l'ENT aux administrateurs de l'établissement.

Il met en œuvre un comportement de recherche sur le sujet et la description des demandes.

## Configuration
Contenu du fichier deployment/support/conf.json.template :
  
   {
      "name": "net.atos~support~1.1-SNAPSHOT",
      "config": {
        "main" : "net.atos.entng.support.Support",
        "port" : 8027,
        "sql" : true,
        "mongodb" : true,
        "neo4j" : true,
        "app-name" : "Aide et support",
        "app-address" : "/support",
        "app-displayName": "support",
        "app-icon" : "support-large",
        "host": "${host}",
        "ssl" : $ssl,
        "auto-redeploy": false,
        "userbook-host": "${host}",
        "integration-mode" : "HTTP",
        "mode" : "${mode}",
        "activate-escalation" : $activateEscalation,
        "bug-tracker-name" : "$SupportBugTrackername",
        "bug-tracker-host" : "support.web-education.net",
        "bug-tracker-port" : 80,
        "bug-tracker-api-key" : "f8ffdbfb0ee9bae2448713d70172b7df0142d270",
        "bug-tracker-projectid" : 39,
        "bug-tracker-resolved-statusid" : 3,
        "bug-tracker-closed-statusid" : 5,
        "escalation-httpclient-maxpoolsize" : 16,
        "escalation-httpclient-keepalive" : false,
        "escalation-httpclient-tryusecompression" : true,
        <% if (SupportBugTrackername != null && "PIVOT".equals(SupportBugTrackername)) { %>
            "user-iws-id" : "$SupportUserIwsId",
            "user-iws-name" : "$SupportUserIwsName",
		<% } %>
        "refresh-period" : 15,
      <% if (swiftUri != null && !swiftUri.trim().isEmpty()) { %>
          "swift" : {
              "uri" : "${swiftUri}",
              "container" : "${swiftContainer}",
              "user" : "${swiftUser}",
              "key" : "${swiftKey}"
          }
      <% } else { %>
            "gridfs-address" : "wse.gridfs.persistor"
      <% } %>
      }
    }

Les paramètres spécifiques à l'application support sont les suivants :

        "activate-escalation" : booléen permettant d'activer / de désactiver l'escalade vers un outil de ticketing externe
        
        Si l'escalade est activée, les paramètres suivants sont nécessaires.
                
        "bug-tracker-name" : "Nom de l'outil de ticketing externelié au module. Les valeurs possibles sont  REDMINE  ou PIVOT"
        
Selon le bugtracker paramètré ci dessus, les paramètres suivants sont nécessaires         

REDMINE : 

        "bug-tracker-host" : hostname du serveur hébergeant Redmine
        "bug-tracker-port" : port du serveur hébergeant Redmine
        "bug-tracker-api-key" : clé associée au compte Redmine utilisé pour l'escalade. Elle permet de faire des appels REST. Cf http://www.redmine.org/projects/redmine/wiki/Rest_api#Authentication
        "bug-tracker-projectid" : identifiant du projet Redmine
        "bug-tracker-resolved-statusid" : entier correspondant au statut "Résolu" dans Redmine
        "bug-tracker-closed-statusid" : entier correspondant au statut "Fermé" dans Redmine
        "refresh-period" : période de rafraîchissement en minutes. L'ENT récupère les données de Redmine et les sauvegarde toutes les "refresh-period" minutes

        "escalation-httpclient-maxpoolsize" : paramètre "maxpoolsize" du client HTTP vert.x utilisé par le module Support pour communiquer avec Redmine en REST
        "escalation-httpclient-keepalive" : paramètre "keepalive" du client HTTP vert.x utilisé par le module Support pour communiquer avec Redmine en REST
        "escalation-httpclient-tryusecompression" : paramètre "tryusecompression" du client HTTP Vert.x utilisé par le module Support pour communiquer avec Redmine en REST

        Se reporter à la javadoc du client HTTP vert.x pour le détail des paramètres : http://vertx.io/api/java/org/vertx/java/core/http/HttpClient.html

PIVOT : outil de ticketing IWS (ISILOG)

        "user-iws-id" : "0 ou identifiant d'un compte dans l'annuaire apparaissant sur les commentaires sur les tickets provnant de IWS",
		"user-iws-name" : "Nom de l'utilisateur affiché pour les commentaires sur les tickets en provenance de IWS",

        IWS nécessite d'avoir installer le module NG supplémentaire SupportPivot.
