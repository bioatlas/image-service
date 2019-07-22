<!doctype html>
<html>
    <head>
        <meta name="layout" content="adminLayout"/>
        <title>Admin</title>
        <meta name="breadcrumbs" content="${g.createLink( controller: 'image', action: 'list')}, Images"/>
    </head>

    <body>
        <content tag="pageTitle">Dashboard</content>
        <content tag="adminButtonBar" />

        <g:if test="${grailsApplication.config.security.cas.disableCAS.toBoolean()}">
            <div class="alert alert-warning" style="display: block">WARNING: CAS authentication disabled - this means admin functions are exposed!</div>
        </g:if>

        <div class="well well-small">
            <h4>Statistics</h4>
            <table class="table table-striped">
                <tr>
                    <td class="col-md-6">Image count</td>
                    <td class="col-md-6"><span id="statImageCount"><asset:image src="spinner.gif" /></span></td>
                </tr>
            </table>
            <h4>Background processing</h4>
            <table class="table">
                <tr>
                    <td class="col-md-6">
                        Import/Thumbnail/Delete queue size
                    </td>
                    <td class="col-md-6">
                        <span id="statQueueSize"><asset:image src="spinner.gif" /></span>
                    </td>
                </tr>
                <tr>
                    <td class="col-md-6">
                        Tiling queue size
                    </td>
                    <td class="col-md-6">
                        <span id="tilingQueueSize"><asset:image src="spinner.gif" /></span>
                    </td>
                </tr>

            </table>
        </div>
        <script>

            $(document).ready(function() {

                updateQueueLength();
                updateRepoStatistics();

                setInterval(updateQueueLength, 5000);
                setInterval(updateRepoStatistics, 5000);
            });

            function updateRepoStatistics() {
                $.ajax("${createLink(controller:'webService', action:'getRepositoryStatistics')}").done(function(data) {
                    $("#statImageCount").html(data.imageCount);
                });
            }

            function updateQueueLength() {
                $.ajax("${createLink(controller:'webService', action:'getBackgroundQueueStats')}").done(function(data) {
                    $("#statQueueSize").html(data.queueLength);
                    $("#tilingQueueSize").html(data.tilingQueueLength);
                });
            }
        </script>
    </body>
</html>
