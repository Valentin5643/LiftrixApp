import { useState } from 'react';
import { doc, updateDoc, Timestamp } from 'firebase/firestore';
import { httpsCallable } from 'firebase/functions';
import { db, auth } from '../firebaseConfig';

const ModerationActionDialog = ({ report, actionType, onClose }) => {
  const [notes, setNotes] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);

  const getActionTitle = () => {
    switch (actionType) {
      case 'hide':
        return 'Hide Content';
      case 'delete':
        return 'Delete Content';
      case 'warn':
        return 'Warn User';
      case 'suspend':
        return 'Suspend User';
      case 'dismiss':
        return 'Dismiss Report';
      default:
        return 'Moderate Content';
    }
  };

  const getActionDescription = () => {
    switch (actionType) {
      case 'hide':
        return `This will hide the ${report.content_type.toLowerCase()} from public view. The content will still exist in the database but won't be visible to users.`;
      case 'delete':
        return `This will permanently delete the ${report.content_type.toLowerCase()}. This action cannot be undone.`;
      case 'warn':
        return `This will send a warning to the user about their ${report.content_type.toLowerCase()}. The content will remain visible.`;
      case 'suspend':
        return `This will temporarily suspend the user's account. They won't be able to post or interact until the suspension is lifted.`;
      case 'dismiss':
        return 'This will mark the report as reviewed and dismissed without taking action.';
      default:
        return 'Please confirm this moderation action.';
    }
  };

  const handleConfirm = async () => {
    if (!notes.trim() && actionType !== 'dismiss') {
      setError('Please provide a reason for this action');
      return;
    }

    setLoading(true);
    setError(null);

    try {
      const currentUser = auth.currentUser;
      if (!currentUser) {
        throw new Error('Not authenticated');
      }

      // Update the report status
      const reportRef = doc(db, 'content_reports', report.id);
      const updateData = {
        status: actionType === 'dismiss' ? 'dismissed' : 'actioned',
        actioned_at: Timestamp.now(),
        actioned_by_admin: currentUser.uid,
        action_taken: actionType,
        admin_notes: notes.trim() || null
      };

      await updateDoc(reportRef, updateData);

      // Execute the moderation action (hide, delete, warn, suspend)
      // Note: In production, these should be Cloud Functions to ensure proper authorization
      if (actionType === 'hide') {
        await hideContent(report);
      } else if (actionType === 'delete') {
        await deleteContent(report);
      } else if (actionType === 'warn') {
        await warnUser(report, notes);
      } else if (actionType === 'suspend') {
        await suspendUser(report, notes);
      }

      onClose();
    } catch (err) {
      console.error('Error executing moderation action:', err);
      setError(err.message || 'Failed to execute action');
    } finally {
      setLoading(false);
    }
  };

  const hideContent = async (report) => {
    // Update the content to set is_hidden = true
    let collectionName;
    switch (report.content_type) {
      case 'POST':
        collectionName = 'workout_posts';
        break;
      case 'COMMENT':
        collectionName = 'post_comments';
        break;
      case 'PROFILE':
        // Profiles use a different structure
        const profileRef = doc(db, 'social_profiles', report.content_id);
        await updateDoc(profileRef, {
          is_hidden: true,
          hidden_at: Timestamp.now(),
          hidden_reason: notes.trim()
        });
        return;
      default:
        throw new Error('Unknown content type');
    }

    const contentRef = doc(db, collectionName, report.content_id);
    await updateDoc(contentRef, {
      is_hidden: true,
      hidden_at: Timestamp.now(),
      hidden_reason: notes.trim()
    });
  };

  const deleteContent = async (report) => {
    // Note: In production, this should be a Cloud Function that properly handles
    // cascading deletes and creates an audit trail

    // For now, we'll just hide it with a deleted flag
    // Real deletion should be handled server-side to ensure data integrity
    await hideContent(report);

    // Mark as deleted (soft delete)
    let collectionName;
    switch (report.content_type) {
      case 'POST':
        collectionName = 'workout_posts';
        break;
      case 'COMMENT':
        collectionName = 'post_comments';
        break;
      case 'PROFILE':
        throw new Error('Cannot delete profiles through this interface');
      default:
        throw new Error('Unknown content type');
    }

    const contentRef = doc(db, collectionName, report.content_id);
    await updateDoc(contentRef, {
      is_deleted: true,
      deleted_at: Timestamp.now()
    });
  };

  const warnUser = async (report, reason) => {
    // Create a moderation action record
    const actionRef = doc(collection(db, 'moderation_actions'));
    await setDoc(actionRef, {
      user_id: report.content_owner_id,
      action_type: 'warning',
      reason: reason.trim(),
      related_report_id: report.id,
      related_content_id: report.content_id,
      created_at: Timestamp.now(),
      created_by_admin: auth.currentUser.uid
    });
  };

  const suspendUser = async (report, reason) => {
    // Create an account restriction
    // Note: This should ideally use a Cloud Function to update Firebase Auth custom claims
    const restrictionRef = doc(collection(db, 'account_restrictions'));
    await setDoc(restrictionRef, {
      user_id: report.content_owner_id,
      restriction_type: 'temporary_suspension',
      reason: reason.trim(),
      related_report_id: report.id,
      created_at: Timestamp.now(),
      created_by_admin: auth.currentUser.uid,
      expires_at: Timestamp.fromDate(new Date(Date.now() + 7 * 24 * 60 * 60 * 1000)) // 7 days
    });
  };

  return (
    <div className="dialog-overlay" onClick={onClose}>
      <div className="dialog" onClick={(e) => e.stopPropagation()}>
        <div className="dialog-header">
          <h2>{getActionTitle()}</h2>
        </div>

        <div className="dialog-body">
          <p style={{ marginBottom: '20px', color: '#666' }}>
            {getActionDescription()}
          </p>

          {error && (
            <div className="error-message" style={{ marginBottom: '20px' }}>
              {error}
            </div>
          )}

          <div className="form-group">
            <label htmlFor="notes">
              {actionType === 'dismiss' ? 'Notes (Optional)' : 'Reason for Action *'}
            </label>
            <textarea
              id="notes"
              value={notes}
              onChange={(e) => setNotes(e.target.value)}
              placeholder="Provide details about why you're taking this action..."
              disabled={loading}
              required={actionType !== 'dismiss'}
            />
          </div>

          <div style={{ background: '#f9f9f9', padding: '12px', borderRadius: '4px', fontSize: '14px' }}>
            <strong>Report Details:</strong>
            <div style={{ marginTop: '8px', color: '#666' }}>
              <div>Content ID: {report.content_id?.substring(0, 16)}...</div>
              <div>Reporter: {report.reporter_id?.substring(0, 16)}...</div>
              <div>Reason: {report.reason?.replace(/_/g, ' ')}</div>
            </div>
          </div>
        </div>

        <div className="dialog-footer">
          <button
            className="btn btn-secondary"
            onClick={onClose}
            disabled={loading}
          >
            Cancel
          </button>
          <button
            className={`btn ${actionType === 'dismiss' ? 'btn-secondary' : 'btn-danger'}`}
            onClick={handleConfirm}
            disabled={loading}
          >
            {loading ? 'Processing...' : 'Confirm'}
          </button>
        </div>
      </div>
    </div>
  );
};

export default ModerationActionDialog;
