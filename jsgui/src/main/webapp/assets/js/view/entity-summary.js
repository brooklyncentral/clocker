/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
*/
/**
 * Render the application/entity summary tab.
 * @type {*}
 */
define([
    "underscore", "jquery", "backbone", "brooklyn", "brooklyn-utils", "view/viewutils",
    "text!tpl/apps/summary.html", "view/entity-config", 
], function (_, $, Backbone, Brooklyn, Util, ViewUtils, 
        SummaryHtml, EntityConfigView) {

    var EntitySummaryView = Backbone.View.extend({
        events:{
            'click a.open-tab':'tabSelected'
        },
        template:_.template(SummaryHtml),
        initialize: function() {
            _.bindAll(this);
            var that = this;
            this.$el.html(this.template({
                entity:this.model,
                application:this.options.application,
                isApp: this.isApp()
            }));
            if (this.model.get('catalogItemId'))
                this.$("div.catalogItemId").show();
            else
                this.$("div.catalogItemId").hide();

            this.options.tabView.configView = new EntityConfigView({
                model:this.options.model,
                tabView:this.options.tabView,
            });
            this.$("div#advanced-config").html(this.options.tabView.configView.render().el);

            ViewUtils.attachToggler(this.$el);

            // TODO we should have a backbone object exported from the sensors view which we can listen to here
            // (currently we just take the URL from that view) - and do the same for active tasks;
            ViewUtils.getRepeatedlyWithDelay(this, this.model.getSensorUpdateUrl(),
                function(data) { that.updateWithData(data); });
            // however if we only use external objects we must either subscribe to their errors also
            // or do our own polling against the server, so we know when to disable ourselves
//            ViewUtils.fetchRepeatedlyWithDelay(this, this.model, { period: 10*1000 })

            this.loadSpec();
        },
        isApp: function() {
            var id = this.model.get('id');
            var selfLink = this.model.get('links').self;
            return selfLink.indexOf("/applications/" + id) != -1;
        },
        render:function () {
            return this;
        },
        revealIfHasValue: function(sensor, $div, renderer, values) {
            var that = this;
            if (!renderer) renderer = function(data) { return _.escape(data); }
            
            if (values) {
                var data = values[sensor]
                if (data || data===false) {
                    $(".value", $div).html(renderer(data));
                    $div.show();
                    return true;
                } else {
                    $div.hide();
                    return false;
                }
            }
        },
        updateWithData: function (data) {
            this.revealIfHasValue("service.state", this.$(".status"), null, data)
            this.revealIfHasValue("service.isUp", this.$(".serviceUp"), null, data)
            
            var renderAsLink = function(data) { return "<a href='"+_.escape(data)+"'>"+_.escape(data)+"</a>" };
            this.revealIfHasValue("mapped.main.uri", this.$(".url"), renderAsLink, data) ||
                this.revealIfHasValue("main.uri", this.$(".url"), renderAsLink, data)

            var status = this.updateStatusIcon();
            
            this.updateCachedProblemIndicator(data);
            
            if (status.problem) {
                this.updateAddlInfoForProblem();
            } else {
                this.$(".additional-info-on-problem").html("").hide()
            }
        },
        updateStatusIcon: function() {
            var statusIconInfo = ViewUtils.computeStatusIconInfo(this.$(".serviceUp .value").html(), this.$(".status .value").html());
            if (statusIconInfo.url) {
                this.$('#status-icon').html('<img src="'+statusIconInfo.url+'" '+
                        'style="max-width: 64px; max-height: 64px;"/>');
            } else {
                this.$('#status-icon').html('');
            }
            return statusIconInfo;
        },
        updateCachedProblemIndicator: function(data) {
            if (!data) return;
            this.problemIndicators = data['service.problems'];
            if (!this.problemIndicators || !_.size(this.problemIndicators))
                this.problemIndicators = data['service.notUp.indicators'];
            if (!this.problemIndicators || !_.size(this.problemIndicators))
                this.problemIndicators = null;
        },
        updateAddlInfoForProblem: function(tasksReloaded) {
            if (!this.options.tasks)
                // if tasks not supplied, then don't attempt to show status info!
                return;
            
            var problemDetails = "";
            var lastFailedTask = null, that = this;
            // ideally get the time the status changed, and return the last failure on or around that time
            // (or take it from some causal log)
            // but for now, we just return the most recent failed task
            this.options.tasks.each(function(it) {
                if (it.isError() && it.isLocalTopLevel()) {
                    if (!lastFailedTask || it.attributes.endTimeUtc < lastFailedTask.attributes.endTimeUtc)
                        lastFailedTask = it;
                }
            } );

            if (this.problemIndicators) {
                var indicatorText = _.values(this.problemIndicators);
                for (var error in indicatorText) {
                    if (problemDetails) {
                        problemDetails = problemDetails + "<br style='line-height: 24px;'>";
                    }
                    problemDetails = problemDetails + _.escape(indicatorText[error]);
                }
            }
            if (lastFailedTask) {
                var path = "activities/subtask/"+lastFailedTask.id;
                var base = this.model.getLinkByName("self");
                if (problemDetails)
                    problemDetails = problemDetails + "<br style='line-height: 24px;'>";
                problemDetails = problemDetails + "<b>"+_.escape("Failure running task ")
                    +"<a class='open-tab' tab-target='"+path+"'" +
                    		"href='#"+base+"/"+path+"'>" +
            				"<i>"+_.escape(lastFailedTask.attributes.displayName)+"</i> "
                    +"("+lastFailedTask.id+")</a>: </b>"+
                    _.escape(lastFailedTask.attributes.result);
            }
            if (!that.problemTasksLoaded && this.options.tasks) {
                // trigger callback to get tasks
                if (!problemDetails)
                    problemDetails = "<i>Loading problem details...</i>";
                
                ViewUtils.get(this, this.options.tasks.url, function() {
                    that.problemTasksLoaded = true;
                    that.updateAddlInfoForProblem();
                });
            }
            
            if (problemDetails) {
                this.$(".additional-info-on-problem").html(problemDetails).show();
            } else {
                var base = this.model.getLinkByName("self");
                this.$(".additional-info-on-problem").html(
                        "The entity appears to have failed externally. " +
                        "<br style='line-height: 24px;'>" +
                        "No Brooklyn-managed task failures reported. " +
                        "For more information, investigate " +
                            "<a class='open-tab' tab-target='sensors' href='#"+base+"/sensors'>sensors</a> and " +
                            "streams on recent " +
                            "<a class='open-tab' tab-target='activities' href='#"+base+"/activities'>activity</a>, " +
                            "as well as external systems and logs where necessary.").show();
            }
        },
        tabSelected: function(event) {
            if (event.metaKey || event.shiftKey)
                // trying to open in a new tab, do not act on it here!
                return;
            var tab = $(event.currentTarget).attr('tab-target');
            this.options.tabView.openTab(tab);
            // and prevent the a from firing
            event.preventDefault();
            return false;
        },
        loadSpec: function(flushCache) {
            if (!flushCache && this.spec) {
                this.renderSpec(this.spec);
                return;
            }
            ViewUtils.get(this, this.model.get('links').spec, this.renderSpec);
        },
        renderSpec: function(data) {
            if (!data) data=this.spec;
            if (!data) {
                this.$('#entity-spec-yaml-toggler').hide();
            } else {
                ViewUtils.updateTextareaWithData($("#entity-spec-yaml", this.$el), data, true, false, 150, 400);
                this.$('#entity-spec-yaml-toggler').show();
            }
        }
    });

    return EntitySummaryView;
});
