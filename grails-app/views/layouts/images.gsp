<%@ page import="au.org.ala.web.CASRoles" %>
<g:applyLayout name="${grailsApplication.config.skin.layout}">
    <head>
        <meta http-equiv="Content-Type" content="text/html; charset=UTF-8"/>
        <meta name="app.version" content="${g.meta(name: 'app.version')}"/>
        <meta name="app.build" content="${g.meta(name: 'app.build')}"/>
        <meta name="description" content="Atlas of Living Australia"/>
        <meta name="author" content="Atlas of Living Australia">
        <meta name="viewport" content="width=device-width, initial-scale=1.0">
        <link href="https://netdna.bootstrapcdn.com/font-awesome/4.1.0/css/font-awesome.min.css" rel="stylesheet">
        <r:require modules="bootstrap, application, qtip, image-viewer"/>

        <style>

            .pagination a {
                text-decoration: none;
            }

            .icon-grey {
                opacity: 0.5;
            }

            .spinner {
                background: url("${g.resource(dir:'images', file:'spinner.gif')}") 50% 50% no-repeat transparent;
                height: 16px;
                width: 16px;
                padding: 0.5em;
                position: absolute;
                right: 0;
                top: 0;
                text-indent: -9999px;
            }

    </style>
        <r:script disposition='head'>
            var IMAGES_CONF = {

            }
        </r:script>

        <r:script disposition='head'>

            // initialise plugins
            jQuery(function () {
                // autocomplete on navbar search input
                jQuery("form#search-form-2011 input#search-2011, form#search-inpage input#search, input#search-2013").autocomplete('https://bie.ala.org.au/search/auto.jsonp', {
                    extraParams: {limit: 100},
                    dataType: 'jsonp',
                    parse: function (data) {
                        var rows = new Array();
                        data = data.autoCompleteList;
                        for (var i = 0; i < data.length; i++) {
                            rows[i] = {
                                data: data[i],
                                value: data[i].matchedNames[0],
                                result: data[i].matchedNames[0]
                            };
                        }
                        return rows;
                    },
                    matchSubset: false,
                    formatItem: function (row, i, n) {
                        return row.matchedNames[0];
                    },
                    cacheLength: 10,
                    minChars: 3,
                    scroll: false,
                    max: 10,
                    selectFirst: false
                });

                // Mobile/desktop toggle
                // TODO: set a cookie so user's choice is remembered across pages
                var responsiveCssFile = $("#responsiveCss").attr("href"); // remember set href
                $(".toggleResponsive").click(function (e) {
                    e.preventDefault();
                    $(this).find("i").toggleClass("icon-resize-small icon-resize-full");
                    var currentHref = $("#responsiveCss").attr("href");
                    if (currentHref) {
                        $("#responsiveCss").attr("href", ""); // set to desktop (fixed)
                        $(this).find("span").html("Mobile");
                    } else {
                        $("#responsiveCss").attr("href", responsiveCssFile); // set to mobile (responsive)
                        $(this).find("span").html("Desktop");
                    }
                });

            });

            function updateSelectionContext() {
                $.ajax("${createLink(controller:'selection', action:'userContextFragment')}").done(function(content) {
                    $("#selectionContext").html(content);
                });
            }

            function updateAlbums() {
                $.ajax("${createLink(controller:'album', action:'userContextFragment')}").done(function(content) {
                    $("#albums-div").html(content);
                });
            }


            function loadingSpinner() {
                return '<img src="${g.resource(dir:'images', file:'spinner.gif')}"/>&nbsp;Loading...';
            }

            $(document).ready(function() {
                updateSelectionContext();
                updateAlbums();
            });

        </r:script>

        <g:layoutHead/>
    </head>

    <body class="${pageProperty(name: 'body.class')}" id="${pageProperty(name: 'body.id')}" onload="${pageProperty(name: 'body.onload')}">

        <g:set var="containerClass" value="container"/>
        <g:if test="${pageProperty(name:'page.useFluidLayout')}">
            <g:set var="containerClass" value="container-fluid"/>
        </g:if>

        <div class="container">
            <header id="page-header">
                <div class="container">
                    <hgroup>
                        <div class="row-fluid">
                            <div class="span8">
                                <g:pageProperty name="page.page-header" />
                            </div>
                            <div class="span4">
                                <div class="pull-right">
                                    <auth:ifLoggedIn>
                                        <span id="albums-div" style="display: inline-block"></span>
                                        <span id="selectionContext" style="display: inline-block"></span>
                                        <a href="${createLink(controller:'image', action:'stagedImages')}" class="btn btn-small btn-success"><i class="icon-plus icon-white"></i>Upload</a>
                                    </auth:ifLoggedIn>
                                    <auth:ifAnyGranted roles="${au.org.ala.web.CASRoles.ROLE_ADMIN}">
                                        <a href="${createLink(controller:'admin', action:'index')}" class="btn btn-warning btn-small"><i class="icon-cog icon-white"></i>Admin</a>
                                    </auth:ifAnyGranted>
                                </div>
                            </div>
                        </div>
                    </hgroup>
                    <g:if test="${flash.message}">
                        <div class="alert alert-info" role="status">${flash.message}</div>
                    </g:if>

                    <g:if test="${flash.errorMessage}">
                        <div class="alert alert-error" role="status">${flash.errorMessage}</div>
                    </g:if>
                </div>
            </header>
        </div>
        <div class="${containerClass}" id="main-content">
            <g:layoutBody/>
        </div><!--/.container-->

        <div class="spinner well well-small" style="display: none"></div>

        <div class="container hidden-desktop">
            <%-- Borrowed from http://marcusasplund.com/optout/ --%>
            <a class="btn btn-small toggleResponsive"><i class="icon-resize-full"></i> <span>Desktop</span> version</a>
        </div>
    </body>
</g:applyLayout>
