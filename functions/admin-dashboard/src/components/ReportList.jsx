import { useState, useEffect } from 'react';
import { collection, query, where, orderBy, onSnapshot, Timestamp } from 'firebase/firestore';
import { db } from '../firebaseConfig';
import ModerationActionDialog from './ModerationActionDialog';

const ReportList = () => {
  const [reports, setReports] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  // Filter state
  const [filterType, setFilterType] = useState('all');
  const [filterReason, setFilterReason] = useState('all');
  const [filterStatus, setFilterStatus] = useState('pending');
  const [filterDateFrom, setFilterDateFrom] = useState('');

  // Dialog state
  const [selectedReport, setSelectedReport] = useState(null);
  const [actionType, setActionType] = useState(null);
  const [showDialog, setShowDialog] = useState(false);

  useEffect(() => {
    let q = collection(db, 'content_reports');
    const constraints = [];

    // Status filter (always applied)
    if (filterStatus !== 'all') {
      constraints.push(where('status', '==', filterStatus));
    }

    // Type filter
    if (filterType !== 'all') {
      constraints.push(where('content_type', '==', filterType.toUpperCase()));
    }

    // Reason filter
    if (filterReason !== 'all') {
      constraints.push(where('reason', '==', filterReason.toUpperCase()));
    }

    // Date filter
    if (filterDateFrom) {
      const fromDate = Timestamp.fromDate(new Date(filterDateFrom));
      constraints.push(where('created_at', '>=', fromDate));
    }

    // Order by creation date (newest first)
    constraints.push(orderBy('created_at', 'desc'));

    if (constraints.length > 0) {
      q = query(q, ...constraints);
    }

    setLoading(true);
    const unsubscribe = onSnapshot(
      q,
      (snapshot) => {
        const reportsData = snapshot.docs.map(doc => ({
          id: doc.id,
          ...doc.data()
        }));
        setReports(reportsData);
        setLoading(false);
        setError(null);
      },
      (err) => {
        console.error('Error fetching reports:', err);
        setError('Failed to load reports: ' + err.message);
        setLoading(false);
      }
    );

    return () => unsubscribe();
  }, [filterType, filterReason, filterStatus, filterDateFrom]);

  const handleAction = (report, action) => {
    setSelectedReport(report);
    setActionType(action);
    setShowDialog(true);
  };

  const handleDialogClose = () => {
    setShowDialog(false);
    setSelectedReport(null);
    setActionType(null);
  };

  const formatDate = (timestamp) => {
    if (!timestamp) return 'N/A';
    const date = timestamp.toDate ? timestamp.toDate() : new Date(timestamp);
    return new Intl.DateTimeFormat('en-US', {
      year: 'numeric',
      month: 'short',
      day: 'numeric',
      hour: '2-digit',
      minute: '2-digit'
    }).format(date);
  };

  const getContentPreview = (report) => {
    // For different content types, show appropriate preview
    if (report.content_type === 'POST' && report.content_snapshot?.caption) {
      return report.content_snapshot.caption.substring(0, 150) + (report.content_snapshot.caption.length > 150 ? '...' : '');
    }
    if (report.content_type === 'COMMENT' && report.content_snapshot?.text) {
      return report.content_snapshot.text.substring(0, 150) + (report.content_snapshot.text.length > 150 ? '...' : '');
    }
    if (report.content_type === 'PROFILE' && report.content_snapshot?.bio) {
      return `Bio: ${report.content_snapshot.bio}`;
    }
    return 'No preview available';
  };

  if (loading && reports.length === 0) {
    return (
      <div className="loading">
        <div className="spinner"></div>
        <p>Loading reports...</p>
      </div>
    );
  }

  return (
    <>
      <div className="filters">
        <h3>Filters</h3>
        <div className="filter-row">
          <div className="filter-group">
            <label htmlFor="status">Status</label>
            <select
              id="status"
              value={filterStatus}
              onChange={(e) => setFilterStatus(e.target.value)}
            >
              <option value="pending">Pending</option>
              <option value="actioned">Actioned</option>
              <option value="dismissed">Dismissed</option>
              <option value="all">All</option>
            </select>
          </div>

          <div className="filter-group">
            <label htmlFor="type">Content Type</label>
            <select
              id="type"
              value={filterType}
              onChange={(e) => setFilterType(e.target.value)}
            >
              <option value="all">All Types</option>
              <option value="post">Post</option>
              <option value="comment">Comment</option>
              <option value="profile">Profile</option>
            </select>
          </div>

          <div className="filter-group">
            <label htmlFor="reason">Reason</label>
            <select
              id="reason"
              value={filterReason}
              onChange={(e) => setFilterReason(e.target.value)}
            >
              <option value="all">All Reasons</option>
              <option value="spam">Spam</option>
              <option value="harassment">Harassment</option>
              <option value="hate_speech">Hate Speech</option>
              <option value="inappropriate">Inappropriate Content</option>
              <option value="misinformation">Misinformation</option>
              <option value="other">Other</option>
            </select>
          </div>

          <div className="filter-group">
            <label htmlFor="dateFrom">From Date</label>
            <input
              type="date"
              id="dateFrom"
              value={filterDateFrom}
              onChange={(e) => setFilterDateFrom(e.target.value)}
            />
          </div>
        </div>
      </div>

      {error && (
        <div className="error-message">
          {error}
        </div>
      )}

      <div className="reports-list">
        {reports.length === 0 ? (
          <div className="empty-state">
            <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
            </svg>
            <p>No reports found matching your filters</p>
          </div>
        ) : (
          reports.map((report) => (
            <div key={report.id} className="report-card">
              <div className="report-header">
                <div className="report-info">
                  <h4>
                    {report.content_type} Report - {report.reason?.replace(/_/g, ' ')}
                  </h4>
                  <div className="report-meta">
                    <span>
                      <strong>ID:</strong> {report.id.substring(0, 8)}...
                    </span>
                    <span>
                      <strong>Reported:</strong> {formatDate(report.created_at)}
                    </span>
                    <span>
                      <strong>Reporter:</strong> {report.reporter_id?.substring(0, 8)}...
                    </span>
                  </div>
                </div>
                <span className={`report-status ${report.status}`}>
                  {report.status}
                </span>
              </div>

              <div className="report-preview">
                {getContentPreview(report)}
              </div>

              {report.reporter_notes && (
                <div style={{ marginBottom: '15px', fontSize: '14px', color: '#666' }}>
                  <strong>Reporter Notes:</strong> {report.reporter_notes}
                </div>
              )}

              {report.status === 'pending' && (
                <div className="report-actions">
                  <button
                    className="btn btn-danger"
                    onClick={() => handleAction(report, 'hide')}
                  >
                    Hide Content
                  </button>
                  <button
                    className="btn btn-danger"
                    onClick={() => handleAction(report, 'delete')}
                  >
                    Delete Content
                  </button>
                  <button
                    className="btn btn-warning"
                    onClick={() => handleAction(report, 'warn')}
                  >
                    Warn User
                  </button>
                  <button
                    className="btn btn-warning"
                    onClick={() => handleAction(report, 'suspend')}
                  >
                    Suspend User
                  </button>
                  <button
                    className="btn btn-secondary"
                    onClick={() => handleAction(report, 'dismiss')}
                  >
                    Dismiss Report
                  </button>
                </div>
              )}

              {report.status === 'actioned' && report.action_taken && (
                <div style={{ fontSize: '14px', color: '#2e7d32', marginTop: '10px' }}>
                  <strong>Action Taken:</strong> {report.action_taken} by {report.actioned_by_admin?.substring(0, 8)}... on {formatDate(report.actioned_at)}
                  {report.admin_notes && <div><strong>Notes:</strong> {report.admin_notes}</div>}
                </div>
              )}
            </div>
          ))
        )}
      </div>

      {showDialog && (
        <ModerationActionDialog
          report={selectedReport}
          actionType={actionType}
          onClose={handleDialogClose}
        />
      )}
    </>
  );
};

export default ReportList;
