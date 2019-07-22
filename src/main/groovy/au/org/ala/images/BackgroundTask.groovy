package au.org.ala.images

abstract class BackgroundTask {

    public List<BackgroundTaskObserver> _observers;

    abstract void execute();

    protected void yieldResult(Object result) {
        if (_observers != null) {
            _observers.each { observer ->
                try {
                    observer.onTaskResult(this, result)
                } catch (Exception ex) {
                    ex.printStackTrace()
                }
            }
        }
    }

    void addObserver(BackgroundTaskObserver observer) {
        if (_observers == null) {
            _observers = new ArrayList<BackgroundTaskObserver>()
        }

        if (!_observers.contains(observer)) {
            _observers.add(observer);
        }
    }

}

interface BackgroundTaskObserver {
    void onTaskResult(BackgroundTask task, Object result);
}
