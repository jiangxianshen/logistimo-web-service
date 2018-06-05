/*
 * Copyright © 2018 Logistimo.
 *
 * This file is part of Logistimo.
 *
 * Logistimo software is a mobile & web platform for supply chain management and remote temperature monitoring in
 * low-resource settings, made available under the terms of the GNU Affero General Public License (AGPL).
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU Affero General
 * Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any
 * later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU Affero General Public License
 * for more details.
 *
 * You should have received a copy of the GNU Affero General Public License along with this program.  If not, see
 * <http://www.gnu.org/licenses/>.
 *
 * You can be released from the requirements of the license by purchasing a commercial license. To know more about
 * the commercial license, please contact us at opensource@logistimo.com
 */

var trnServices = angular.module('trnServices', []);

trnServices.factory('trnService', ['APIService', function (apiService) {
    return {
        getEntityTransactions: function (entityId, tag, from, to, type, offset, size,lEntityId,bId,atd) {
            offset = typeof offset !== 'undefined' ? offset : 0;
            size = typeof size !== 'undefined' ? size : 50;
            atd = atd === true?true:false;
            var urlStr = '/s2/api/transactions/entity/' + entityId + "?offset=" + offset + "&size=" + size+"&atd="+atd;
            if (typeof tag !== 'undefined' && tag != null && tag != "") {
                urlStr = urlStr + "&tag=" + tag;
            }
            if (typeof type !== 'undefined' && type != null && type != "") {
                urlStr = urlStr + "&type=" + type;
            }
            if (typeof from !== 'undefined' && from != null && from != "") {
                urlStr = urlStr + "&from=" + from;
            }
            if (typeof to !== 'undefined' && to != null && to != "") {
                urlStr = urlStr + "&to=" + to;
            }
            if (typeof lEntityId !== 'undefined' && lEntityId != null && lEntityId != "") {
                urlStr = urlStr + "&lEntityId=" + lEntityId;
            }
            if (typeof bId !== 'undefined' && bId != null && bId != "") {
                urlStr = urlStr + "&bId=" + bId;
            }
            if (typeof atd !== 'undefined' && atd != null && atd != "") {
                urlStr = urlStr + "&atd=" + atd;
            }

            return apiService.get(urlStr);
        },
        getMaterialTransactions: function (materialId, ktag, from, to, type, offset, size,bId,atd) {
            offset = typeof offset !== 'undefined' ? offset : 0;
            size = typeof size !== 'undefined' ? size : 50;
            atd = atd === true?true:false;
            var urlStr = '/s2/api/transactions/material/' + materialId + "?offset=" + offset + "&size=" + size+"&atd="+atd;
            if (typeof ktag !== 'undefined' && ktag != null && ktag != "") {
                urlStr = urlStr + "&ktag=" + ktag;
            }
            if (typeof type !== 'undefined' && type != null && type != "") {
                urlStr = urlStr + "&type=" + type;
            }
            if (typeof from !== 'undefined' && from != null && from != "") {
                urlStr = urlStr + "&from=" + from;
            }
            if (typeof to !== 'undefined' && to != null && to != "") {
                urlStr = urlStr + "&to=" + to;
            }
            if (typeof bId !== 'undefined' && bId != null && bId != "") {
                urlStr = urlStr + "&bId=" + bId;
            }
            if (typeof atd !== 'undefined' && atd != null && atd != "") {
                urlStr = urlStr + "&atd=" + atd;
            }
            return apiService.get(urlStr);
        },
        getTransactions: function (etag, tag, from, to, type, offset, size, bId, atd, eid, lEntityId, mid, rsn, ignoreLkid) {
            offset = typeof offset !== 'undefined' ? offset : 0;
            size = typeof size !== 'undefined' ? size : 50;
            atd = atd === true;
            var urlStr = '/s2/api/transactions/?offset=' + offset + "&size=" + size+"&atd="+atd;
            if (typeof etag !== 'undefined' && etag != null && etag != "") {
                urlStr = urlStr + "&ktag=" + etag;
            }
            if (typeof tag !== 'undefined' && tag != null && tag != "") {
                urlStr = urlStr + "&tag=" + tag;
            }
            if (typeof type !== 'undefined' && type != null && type != "") {
                urlStr = urlStr + "&type=" + type;
            }
            if (typeof from !== 'undefined' && from != null && from != "") {
                urlStr = urlStr + "&from=" + from;
            }
            if (typeof to !== 'undefined' && to != null && to != "") {
                urlStr = urlStr + "&to=" + to;
            }
            if (typeof bId !== 'undefined' && bId != null && bId != "") {
                urlStr = urlStr + "&bId=" + bId;
            }
            if (typeof mid !== 'undefined' && mid != null && mid != "") {
                urlStr = urlStr + "&mid=" + mid;
            }
            if (typeof eid !== 'undefined' && eid != null && eid != "") {
                urlStr = urlStr + "&eid=" + eid;
            }
            if (typeof lEntityId !== 'undefined' && lEntityId != null && lEntityId != "") {
                urlStr = urlStr + "&lEntityId=" + lEntityId;
            }
            if (typeof rsn !== 'undefined' && rsn != null && rsn != "") {
                urlStr = urlStr + "&reason=" + rsn;
            }
            if (typeof ignoreLkid !== 'undefined' && ignoreLkid != null && ignoreLkid != "") {
                urlStr = urlStr + "&ignoreLkid=" + ignoreLkid;
            }
            return apiService.get(urlStr);
        },
        undoTransactions: function (tran) {
            return apiService.post(tran, '/s2/api/transactions/undo');
        },
        getTransDomainConfig: function (kioskId) {
            return apiService.get('/s2/api/transactions/transconfig/?kioskId=' + kioskId);
        },
        updateTransaction : function(data){
            return apiService.post(data, '/s2/api/transactions/add/');
        },
        getActualRoute: function(userId,from,to){
            return apiService.get('/s2/api/transactions/actualroute?userId=' + userId + '&from=' + from + '&to=' + to);
        },
        getReasons : function(type,tags){
            var urlStr = '/s2/api/transactions/reasons?type=' + type;
            if (checkNotNullEmpty(tags)) {
                urlStr = urlStr + "&tags=" + tags;
            }
            return apiService.get(urlStr);
        },
        getMatStatus : function(type,ts){
            return apiService.get('/s2/api/transactions/matStatus?type=' + type + '&ts=' + ts);
        },
        getPermission : function(userId,kioskId){
            return apiService.get('/s2/api/transactions/checkpermission?userId=' + userId + '&kioskId=' + kioskId);
        },
        getStatusMandatory : function() {
            return apiService.get('/s2/api/transactions/statusmandatory');
        },
        getTransactionTypesWithReasonMandatory : function() {
            return apiService.get('/s2/api/transactions/transaction-types-with-reason-mandatory')
        }

    }
}]);
