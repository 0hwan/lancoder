package drfoliberg.common.network.messages.cluster;

import drfoliberg.common.network.Cause;
import drfoliberg.common.network.Routes;

public class CrashReport extends AuthMessage {

	private static final long serialVersionUID = 8218452128017064495L;

	private Cause cause;
	private StatusReport statusReport;

	/**
	 * This crash report will help determine which nodes are bad.
	 * 
	 * @param unid
	 *            The unique node identifier
	 * @param cause
	 *            The cause
	 * @param statusReport
	 *            A status report of the node at the crash
	 */
	public CrashReport(String unid, Cause cause, StatusReport statusReport) {
		super(Routes.NODE_CRASH, unid);
		this.cause = cause;
		this.statusReport = statusReport;
	}

	public Cause getCause() {
		return cause;
	}

	public void setCause(Cause cause) {
		this.cause = cause;
	}

	public StatusReport getStatusReport() {
		return statusReport;
	}

	public void setStatusReport(StatusReport statusReport) {
		this.statusReport = statusReport;
	}

}
