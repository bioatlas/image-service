<!doctype html>
<html>
    <head>
        <meta name="layout" content="main"/>
        <meta name="section" content="home"/>
        <title>Advanced search</title>
        <meta name="breadcrumbs" content="${g.createLink( controller: 'image', action: 'list')}, Images"/>
        <asset:stylesheet src="search.css" />
        <asset:javascript src="search.js" />
    </head>

    <body class="content">
        <div class="container-fluid">
            <h1>Advanced search</h1>
            <div class="row-fluid">
                <div class="well well-small">
                    <button type="button" id="btnAddCriteria" class="btn btn-small btn-info"><i class="icon-plus icon-white"></i>&nbsp;Add Criteria</button>
                    <button type="button" id="btnSearch" class="btn btn-primary pull-right">
                        <i class="icon-search icon-white"></i>&nbsp;Search
                    </button>
                    <button type="button" id="btnStartOver" class="btn btn-default pull-right" style="margin-right: 5px">
                        <i class="icon-remove-circle"></i>&nbsp;Start over
                    </button>
                    <div class="row-fluid">
                        <div id="searchCriteria">
                        </div>
                    </div>
                </div>
            </div>
        </div>
        <div id="addCriteriaModal" class="modal fade" role="dialog">
            <div class="modal-dialog">
                <div class="modal-content">
                    <div class="modal-header">
                        <button type="button" class="close" data-dismiss="modal">&times;</button>
                        <h4 class="modal-title">Add Search Criteria</h4>
                    </div>
                    <div class="modal-body">
                        <form id="criteriaForm">
                            <div class="control-group">
                                <label class="control-label" for='searchCriteriaDefinitionId'>Criteria:</label>
                                <g:select class="form-control" id="cmbCriteria" name="searchCriteriaDefinitionId" from="${criteriaDefinitions}"
                                          optionValue="name" optionKey="id" noSelection="${[0:"<Select Criteria>"]}" />
                            </div>
                            <div id="criteriaDetail" style="margin-top:10px;">

                            </div>
                        </form>
                    </div>
                    <div class="modal-footer">
                        <button id="btnSaveCriteria" type="button" class="btn btn-small btn-primary pull-right">Add criteria</button>
                        <button type="button" class="btn btn-default" data-dismiss="modal">Close</button>
                    </div>
                </div>
            </div>
        </div>

        <div id="searchResults"></div>

        <script>

        $(document).ready(function() {
            $("#btnAddCriteria").click(function (e) {
                e.preventDefault();
                $('#addCriteriaModal').modal('show');
            });

            $("#btnStartOver").click(function(e) {
                e.preventDefault();
                $.ajax("${createLink(action:"ajaxClearSearchCriteria", controller:"search")}").done(function() {
                    renderCriteria();
                    clearResults();
                });
            });

            $("#btnSearch").click(function(e) {
                e.preventDefault();
                doSearch();
            });


            $("#cmbCriteria").on('change', function(e) {
                // $("#criteriaDetail").html(loadingSpinner());
                var criteriaDefinitionId = $(this).val();
                if (criteriaDefinitionId == 0) {
                    $("#criteriaDetail").html("");
                    $("#addButtonDiv").css('display', 'none');
                } else {
                    // $("#criteriaDetail").html(loadingSpinner());
                    $.ajax("${createLink(action: "criteriaDetailFragment",  controller:"search")}?searchCriteriaDefinitionId=" + criteriaDefinitionId).done(function(content) {
                        $("#addButtonDiv").css("display", "block");
                        $("#criteriaDetail").html(content);
                    });
                }
            });

            $("#btnSaveCriteria").on('click', function(e) {

                console.log('save criteria');
                var formData = $("#criteriaForm").serialize();
                var errorDiv = $("#errorMessageDiv");
                errorDiv.css("display",'none');
                $.post('${createLink(action:'ajaxAddSearchCriteria',  controller:"search")}',formData, function(data) {
                    if (data.errorMessage) {
                        errorDiv.html(data.errorMessage);
                        errorDiv.css("display",'block');
                    } else {
                        console.log(data);
                        renderCriteria()
                        $('#addCriteriaModal').modal('hide');
                    }
                });
            });

            <g:if test="${hasCriteria}">
                renderCriteria();
                doSearch();
            </g:if>

            });

            function clearResults() {
                $("#searchResults").html("");
            }

            function doSearch() {
                doAjaxSearch("${createLink(action:'searchResultsFragment',  controller:'search')}");
            }

            function doAjaxSearch(url) {
                $("#searchResults").html('<div>Searching...<img src="${resource(dir:'images', file:'spinner.gif')}"></img></div>');
                $.ajax(url).done(function(content) {
                    $("#searchResults").html(content);
                    $(".pagination a").click(function(e) {
                        e.preventDefault();
                        doAjaxSearch($(this).attr("href"));
                    });
                    layoutImages();
                });
            }

            function renderCriteria() {
                $.ajax("${createLink(action: 'criteriaListFragment',  controller:"search", params:[:])}").done(function (content) {
                    $("#searchCriteria").html(content);
                });
            }
        </script>
    </body>
</html>


