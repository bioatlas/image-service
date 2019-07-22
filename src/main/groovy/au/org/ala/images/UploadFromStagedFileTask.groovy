package au.org.ala.images

class UploadFromStagedFileTask extends BackgroundTask {

    private StagedFile _stagedFile
    private Map<String, String> _metaData
    private ImageStagingService _imageStagingService
    private String _batchId
    private boolean _harvestable;

    public UploadFromStagedFileTask(StagedFile stagedFile, Map<String, String> metadata, ImageStagingService imageStagingService, String batchId, boolean harvestable) {
        _stagedFile = stagedFile
        _metaData = metadata
        _imageStagingService = imageStagingService
        _batchId = batchId
        _harvestable = harvestable
    }

    @Override
    void execute() {
        this.yieldResult(_imageStagingService.importFileFromStagedFile(_stagedFile, _batchId, _metaData, _harvestable))
    }

}
