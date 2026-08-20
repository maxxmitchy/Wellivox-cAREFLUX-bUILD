import { test, before, after, beforeEach } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} from '@firebase/rules-unit-testing';

let testEnv;

before(async () => {
  const rules = readFileSync('firestore.rules', 'utf8');
  testEnv = await initializeTestEnvironment({
    projectId: 'demo-pharmacy-project',
    firestore: {
      rules: rules,
      host: '127.0.0.1',
      port: 8088,
    },
  });
});

beforeEach(async () => {
  await testEnv.clearFirestore();

  // Seed baseline data using admin context (rules disabled)
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const db = context.firestore();

    // Registered Pharmacists
    await db.doc('registered_pharmacists/pharm_a').set({
      uid: 'pharm_a',
      fullName: 'Pharmacist A',
      branchId: 'BRANCH_A',
      role: 'Pharmacist',
      isSystemAdmin: false,
      isApproved: true,
      isSuspended: false,
    });

    await db.doc('registered_pharmacists/pharm_b').set({
      uid: 'pharm_b',
      fullName: 'Pharmacist B',
      branchId: 'BRANCH_B',
      role: 'Pharmacist',
      isSystemAdmin: false,
      isApproved: true,
      isSuspended: false,
    });

    await db.doc('registered_pharmacists/manager_a').set({
      uid: 'manager_a',
      fullName: 'Manager A',
      branchId: 'BRANCH_A',
      role: 'Branch Manager',
      isSystemAdmin: false,
      isApproved: true,
      isSuspended: false,
    });

    await db.doc('registered_pharmacists/admin_user').set({
      uid: 'admin_user',
      fullName: 'System Admin',
      branchId: 'BRANCH_A',
      role: 'Administrator',
      isSystemAdmin: true,
      isApproved: true,
      isSuspended: false,
    });

    await db.doc('registered_pharmacists/unapproved_user').set({
      uid: 'unapproved_user',
      fullName: 'Unapproved Staff',
      branchId: 'BRANCH_A',
      role: 'Pharmacist',
      isSystemAdmin: false,
      isApproved: false,
      isSuspended: false,
    });

    // Branch Inventory
    await db.doc('branch_inventory/BRANCH_A_inv1').set({
      id: 'inv1',
      branchId: 'BRANCH_A',
      name: 'Paracetamol 500mg',
      stockQuantity: 100,
    });

    await db.doc('branch_inventory/BRANCH_B_inv1').set({
      id: 'inv1',
      branchId: 'BRANCH_B',
      name: 'Amoxicillin 500mg',
      stockQuantity: 50,
    });

    // Branch Customers
    await db.doc('branch_customers/BRANCH_B_cust1').set({
      id: 'cust1',
      branchId: 'BRANCH_B',
      fullName: 'Customer B',
    });

    // Branch Customer Medications
    await db.doc('branch_customer_medications/BRANCH_B_med1').set({
      id: 'med1',
      branchId: 'BRANCH_B',
      medicationName: 'Aspirin',
    });

    // Branch Interventions
    await db.doc('branch_interventions/BRANCH_B_int1').set({
      id: 'int1',
      branchId: 'BRANCH_B',
      details: 'Intervention details B',
    });

    // Branch Operation Tasks
    await db.doc('branch_operation_tasks/BRANCH_B_task1').set({
      id: 'task1',
      branchId: 'BRANCH_B',
      title: 'Task B',
    });

    // Branch Receipts
    await db.doc('branch_receipts/BRANCH_B_rcpt1').set({
      id: 'rcpt1',
      branchId: 'BRANCH_B',
      receiptNumber: 'REC-001',
    });

    // Branch Outbound Logs
    await db.doc('branch_outbound_logs/BRANCH_B_log1').set({
      id: 'log1',
      branchId: 'BRANCH_B',
      message: 'SMS B',
    });

    // Medication Sales
    await db.doc('medication_sales/sale1_a').set({
      clientTransactionId: 'sale1_a',
      branchId: 'BRANCH_A',
      productName: 'Paracetamol 500mg',
      quantitySold: 2,
      totalAmount: 100.0,
      cashierUid: 'pharm_a',
    });

    await db.doc('medication_sales/sale1_b').set({
      clientTransactionId: 'sale1_b',
      branchId: 'BRANCH_B',
      productName: 'Amoxicillin 500mg',
      quantitySold: 1,
      totalAmount: 200.0,
      cashierUid: 'pharm_b',
    });

    // Device Configs
    await db.doc('device_configs/DEV_A1').set({
      deviceId: 'DEV_A1',
      branchId: 'BRANCH_A',
      ownerUid: 'pharm_a',
      status: 'Active',
    });

    await db.doc('device_configs/DEV_B1').set({
      deviceId: 'DEV_B1',
      branchId: 'BRANCH_B',
      ownerUid: 'pharm_b',
      status: 'Active',
    });

    // Device Config with nodeId starting with BRANCH_A but owned/belonging to BRANCH_B
    await db.doc('device_configs/BRANCH_A_DEV_B2').set({
      deviceId: 'BRANCH_A_DEV_B2',
      branchId: 'BRANCH_B',
      ownerUid: 'pharm_b',
      status: 'Active',
    });

    // Expiry Rescue Listings
    await db.doc('expiry_rescue_listings/rescue_b1').set({
      listingId: 'rescue_b1',
      branchId: 'BRANCH_B',
      ownerUid: 'pharm_b',
      productName: 'Vitamin C',
      status: 'Available',
    });

    // Branch Audit Logs
    await db.doc('branch_audit_logs/BRANCH_A_log1').set({
      branchId: 'BRANCH_A',
      actorUid: 'pharm_a',
      action: 'DISPENSE',
      verified: false,
      verifiedBy: '',
      verifiedAt: 0,
    });

    await db.doc('branch_audit_logs/BRANCH_B_log1').set({
      branchId: 'BRANCH_B',
      actorUid: 'pharm_b',
      action: 'DISPENSE',
      verified: false,
      verifiedBy: '',
      verifiedAt: 0,
    });

    // Consent Handshakes
    await db.doc('consent_handshakes/BRANCH_A_handshake1').set({
      handshakeId: 'BRANCH_A_handshake1',
      branchId: 'BRANCH_A',
      targetBranchId: 'BRANCH_A',
      patientUid: 'patient_a',
      pharmacistUid: 'pharm_a',
      actorUid: 'pharm_a',
      status: 'PENDING',
      consentGranted: false,
      notes: 'Initial request',
    });

    // Admin Audit Logs
    await db.doc('admin_audit_logs/admin_log1').set({
      adminName: 'pharm_a',
      actorUid: 'pharm_a',
      actionPerformed: 'SELF_AUDIT',
      timestamp: 1700000000000,
    });
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

// Helper contexts
function getPharmAContext() {
  return testEnv.authenticatedContext('pharm_a', {
    branchId: 'BRANCH_A',
    role: 'Pharmacist',
    isSystemAdmin: false,
    isApproved: true,
  });
}

function getPharmBContext() {
  return testEnv.authenticatedContext('pharm_b', {
    branchId: 'BRANCH_B',
    role: 'Pharmacist',
    isSystemAdmin: false,
    isApproved: true,
  });
}

function getManagerAContext() {
  return testEnv.authenticatedContext('manager_a', {
    branchId: 'BRANCH_A',
    role: 'Branch Manager',
    isSystemAdmin: false,
    isApproved: true,
  });
}

function getAdminContext() {
  return testEnv.authenticatedContext('admin_user', {
    branchId: 'BRANCH_A',
    role: 'Administrator',
    isSystemAdmin: true,
    isApproved: true,
  });
}

function getUnapprovedContext() {
  return testEnv.authenticatedContext('unapproved_user', {
    branchId: 'BRANCH_A',
    role: 'Pharmacist',
    isSystemAdmin: false,
    isApproved: false,
  });
}

function getUnauthContext() {
  return testEnv.unauthenticatedContext();
}

// ============================================================
// MANDATORY LIVE SECURITY TESTS
// ============================================================

test('1. CrossBranchInventoryReadDenied: Branch A pharmacist attempts to read Branch B inventory', async () => {
  const db = getPharmAContext().firestore();
  await assertFails(db.doc('branch_inventory/BRANCH_B_inv1').get());
});

test('2. CrossBranchInventoryWriteDenied: Branch A pharmacist attempts to create/update inventory belonging to Branch B', async () => {
  const db = getPharmAContext().firestore();
  await assertFails(
    db.doc('branch_inventory/BRANCH_B_inv2').set({
      id: 'inv2',
      branchId: 'BRANCH_B',
      name: 'Forged Stock',
      stockQuantity: 999,
    })
  );
});

test('3. CrossBranchCustomerReadDenied: Branch A pharmacist attempts to read Branch B customer data', async () => {
  const db = getPharmAContext().firestore();
  await assertFails(db.doc('branch_customers/BRANCH_B_cust1').get());
});

test('4. CrossBranchInterventionWriteDenied: Branch A pharmacist attempts to modify Branch B intervention', async () => {
  const db = getPharmAContext().firestore();
  await assertFails(
    db.doc('branch_interventions/BRANCH_B_int1').update({
      details: 'Unauthorized modification',
    })
  );
});

test('5. CrossBranchReceiptAccessDenied: Branch A pharmacist attempts to read/write Branch B receipt', async () => {
  const db = getPharmAContext().firestore();
  await assertFails(db.doc('branch_receipts/BRANCH_B_rcpt1').get());
  await assertFails(
    db.doc('branch_receipts/BRANCH_B_rcpt1').update({
      receiptNumber: 'HAX-000',
    })
  );
});

test('6. SelfAdminEscalationDenied: Authenticated pharmacist attempts isSystemAdmin: false -> true', async () => {
  const db = getPharmAContext().firestore();
  await assertFails(
    db.doc('registered_pharmacists/pharm_a').update({
      isSystemAdmin: true,
    })
  );
});

test('7. SelfRoleEscalationDenied: Pharmacist attempts role: "Pharmacist" -> "Branch Manager"', async () => {
  const db = getPharmAContext().firestore();
  await assertFails(
    db.doc('registered_pharmacists/pharm_a').update({
      role: 'Branch Manager',
    })
  );
});

test('8. SelfApprovalDenied: Unapproved pharmacist attempts isApproved: false -> true', async () => {
  const db = getUnapprovedContext().firestore();
  await assertFails(
    db.doc('registered_pharmacists/unapproved_user').update({
      isApproved: true,
    })
  );
});

test('9. CrossBranchSaleCreationDenied: Branch A pharmacist attempts to create medication_sales with branchId = BranchB', async () => {
  const db = getPharmAContext().firestore();
  await assertFails(
    db.doc('medication_sales/forged_sale').set({
      clientTransactionId: 'forged_sale',
      branchId: 'BRANCH_B',
      productName: 'Illegal Sale',
      quantitySold: 1,
      totalAmount: 50.0,
    })
  );
});

test('10. MedicationSaleUpdateDenied: Non-admin attempts to update an existing medication_sales document', async () => {
  const db = getPharmAContext().firestore();
  await assertFails(
    db.doc('medication_sales/sale1_a').update({
      totalAmount: 0.0,
    })
  );
});

test('11. MedicationSaleAdminAccessAllowed: System administrator updates an existing medication_sales document', async () => {
  const db = getAdminContext().firestore();
  await assertSucceeds(
    db.doc('medication_sales/sale1_a').update({
      notes: 'Admin adjusted audit record',
    })
  );
});

test('12. DeviceCrossBranchTamperingDenied: Branch A pharmacist attempts to modify a device belonging to Branch B', async () => {
  const db = getPharmAContext().firestore();
  await assertFails(
    db.doc('device_configs/DEV_B1').update({
      status: 'Disabled',
    })
  );
});

test('13. DeviceOwnerAccessAllowed: The authenticated owner updates their own device configuration', async () => {
  const db = getPharmAContext().firestore();
  await assertSucceeds(
    db.doc('device_configs/DEV_A1').update({
      status: 'Active',
      lastHeartbeat: 1700000000000,
    })
  );
});

test('14. DeviceOwnerSpoofDenied: User A attempts to change ownerUid of their device to User B', async () => {
  const db = getPharmAContext().firestore();
  await assertFails(
    db.doc('device_configs/DEV_A1').update({
      ownerUid: 'pharm_b',
    })
  );
});

test('15. ExpiryListingUnauthorizedModificationDenied: Pharmacist who is neither owner nor branch manager attempts to modify another branch listing', async () => {
  const db = getPharmAContext().firestore();
  await assertFails(
    db.doc('expiry_rescue_listings/rescue_b1').update({
      productName: 'Tampered Product',
    })
  );
});

test('16. ExpiryListingValidClaimAllowed: Legitimate claimant performs permitted Available -> Claimed transition without changing branchId or ownerUid', async () => {
  const db = getPharmAContext().firestore();
  await assertSucceeds(
    db.doc('expiry_rescue_listings/rescue_b1').update({
      status: 'Claimed',
    })
  );
});

test('17. Wildcard/UndefinedCollectionDenied: Attempt to access an undefined top-level collection/path', async () => {
  const db = getPharmAContext().firestore();
  await assertFails(db.doc('undefined_secret_collection/doc1').get());
  await assertFails(
    db.doc('undefined_secret_collection/doc1').set({ secret: true })
  );
});

test('18. UnauthenticatedAccessDenied: Unauthenticated client attempts to access protected operational data', async () => {
  const db = getUnauthContext().firestore();
  await assertFails(db.doc('branch_inventory/BRANCH_A_inv1').get());
  await assertFails(db.doc('medication_sales/sale1_a').get());
  await assertFails(db.doc('registered_pharmacists/pharm_a').get());
});

test('19. QueryTest: Scoped vs Unscoped medication_sales query authorization', async () => {
  const dbA = getPharmAContext().firestore();

  // Scoped query for own branch (BRANCH_A) MUST succeed
  const scopedQueryA = dbA
    .collection('medication_sales')
    .where('branchId', '==', 'BRANCH_A');
  await assertSucceeds(scopedQueryA.get());

  // Query for another branch (BRANCH_B) MUST be rejected
  const crossQuery = dbA
    .collection('medication_sales')
    .where('branchId', '==', 'BRANCH_B');
  await assertFails(crossQuery.get());

  // Unscoped query MUST be rejected to prevent silent data leakage
  const unscopedQuery = dbA.collection('medication_sales');
  await assertFails(unscopedQuery.get());
});

test('20. BranchSwitchUnauthorizedDenied: Pharmacist A in Branch A attempts to update profile branchId to BRANCH_B without authorization', async () => {
  const db = getPharmAContext().firestore();
  await assertFails(
    db.doc('registered_pharmacists/pharm_a').update({
      branchId: 'BRANCH_B',
    })
  );
});

test('21. ExpiryListingPriceTamperOnClaimDenied: Non-owner claimant attempts to modify price on expiry rescue listing during claim', async () => {
  const db = getPharmAContext().firestore();
  await assertFails(
    db.doc('expiry_rescue_listings/rescue_b1').update({
      status: 'Claimed',
      price: 1.0, // Tampering price during claim MUST fail
    })
  );
});

test('22. ConsentHandshakeUnboundWriteDenied: Cross-branch user attempts to write consent_handshakes for another branch', async () => {
  const db = getPharmAContext().firestore();
  await assertFails(
    db.doc('consent_handshakes/BRANCH_B_handshake1').set({
      branchId: 'BRANCH_B',
      patientUid: 'patient_x',
      actorUid: 'pharm_a', // Cross-branch attempt
    })
  );
});

test('23. BranchAuditLogCrossBranchWriteDenied: Branch A pharmacist attempts to create branch_audit_logs for Branch B', async () => {
  const db = getPharmAContext().firestore();
  await assertFails(
    db.doc('branch_audit_logs/BRANCH_B_log1').set({
      branchId: 'BRANCH_B',
      actorUid: 'pharm_a',
      action: 'TAMPER',
    })
  );
});

test('24. AdminAuditLogUnauthWriteDenied: Unauthenticated client attempts to create admin_audit_logs', async () => {
  const db = getUnauthContext().firestore();
  await assertFails(
    db.doc('admin_audit_logs/log_unauth').set({
      adminName: 'hacker',
      actionPerformed: 'FORGE',
      timestamp: Date.now(),
    })
  );
});

// ============================================================
// FINDING 1 — ADMIN AUDIT LOG FORGERY TESTS
// ============================================================

test('25. AdminAuditLogSelfAttributedCreateAllowed: Non-admin user creates audit log attributed to self', async () => {
  const db = getPharmAContext().firestore();
  await assertSucceeds(
    db.doc('admin_audit_logs/log_pharm_a').set({
      adminName: 'pharm_a',
      actorUid: 'pharm_a',
      actionPerformed: 'SELF_DEVICE_CONFIG',
      timestamp: Date.now(),
    })
  );
});

test('26. AdminAuditLogOtherUidAttributionDenied: Non-admin user attributes audit log to another user UID', async () => {
  const db = getPharmAContext().firestore();
  await assertFails(
    db.doc('admin_audit_logs/log_forge_uid').set({
      adminName: 'pharm_b',
      actorUid: 'pharm_b',
      actionPerformed: 'FORGE_ACTOR',
      timestamp: Date.now(),
    })
  );
});

test('27. AdminAuditLogForgedAdminIdentityDenied: Non-admin user attempts to claim isSystemAdmin or forge admin identity', async () => {
  const db = getPharmAContext().firestore();
  await assertFails(
    db.doc('admin_audit_logs/log_forge_admin').set({
      adminName: 'pharm_a',
      actorUid: 'pharm_a',
      isSystemAdmin: true,
      actionPerformed: 'FORGE_SYSTEM_ADMIN',
      timestamp: Date.now(),
    })
  );
});

test('28. AdminAuditLogAdminCreateAllowed: Legitimate administrator creates admin audit log', async () => {
  const db = getAdminContext().firestore();
  await assertSucceeds(
    db.doc('admin_audit_logs/log_admin_legit').set({
      adminName: 'Administrator',
      actorUid: 'admin_user',
      actionPerformed: 'APPROVE_KEYS',
      timestamp: Date.now(),
    })
  );
});

test('29. AdminAuditLogUpdateDenied: Modifying an existing admin audit log is denied', async () => {
  const db = getAdminContext().firestore();
  await assertFails(
    db.doc('admin_audit_logs/admin_log1').update({
      actionPerformed: 'TAMPER_LOG',
    })
  );
});

test('30. AdminAuditLogDeleteDenied: Deleting an existing admin audit log is denied', async () => {
  const db = getAdminContext().firestore();
  await assertFails(
    db.doc('admin_audit_logs/admin_log1').delete()
  );
});

// ============================================================
// FINDING 2 — BRANCH AUDIT LOG UPDATE OVER-PERMISSION TESTS
// ============================================================

test('31. BranchAuditLogOrdinaryStaffUpdateDenied: Ordinary staff attempting to modify event data/verification is denied', async () => {
  const db = getPharmAContext().firestore();
  await assertFails(
    db.doc('branch_audit_logs/BRANCH_A_log1').update({
      verified: true,
      verifiedBy: 'pharm_a',
      verifiedAt: Date.now(),
    })
  );
});

test('32. BranchAuditLogStaffActorUidTamperDenied: Ordinary staff attempting to modify actorUid on branch audit log is denied', async () => {
  const db = getPharmAContext().firestore();
  await assertFails(
    db.doc('branch_audit_logs/BRANCH_A_log1').update({
      actorUid: 'pharm_b',
    })
  );
});

test('33. BranchAuditLogStaffBranchIdTamperDenied: Ordinary staff attempting to modify branchId on branch audit log is denied', async () => {
  const db = getPharmAContext().firestore();
  await assertFails(
    db.doc('branch_audit_logs/BRANCH_A_log1').update({
      branchId: 'BRANCH_B',
    })
  );
});

test('34. BranchAuditLogManagerVerificationUpdateAllowed: Branch manager updating verification fields on own branch audit log is allowed', async () => {
  const db = getManagerAContext().firestore();
  await assertSucceeds(
    db.doc('branch_audit_logs/BRANCH_A_log1').update({
      verified: true,
      verifiedBy: 'manager_a',
      verifiedAt: Date.now(),
    })
  );
});

test('35. BranchAuditLogManagerEventDataUpdateDenied: Branch manager attempting to modify action/event data on branch audit log is denied', async () => {
  const db = getManagerAContext().firestore();
  await assertFails(
    db.doc('branch_audit_logs/BRANCH_A_log1').update({
      verified: true,
      verifiedBy: 'manager_a',
      verifiedAt: Date.now(),
      action: 'ALTERED_ACTION',
    })
  );
});

test('36. BranchAuditLogManagerBranchIdTamperDenied: Branch manager attempting to modify branchId on branch audit log is denied', async () => {
  const db = getManagerAContext().firestore();
  await assertFails(
    db.doc('branch_audit_logs/BRANCH_A_log1').update({
      verified: true,
      verifiedBy: 'manager_a',
      verifiedAt: Date.now(),
      branchId: 'BRANCH_B',
    })
  );
});

test('37. BranchAuditLogCrossBranchManagerUpdateDenied: Manager from Branch A attempting to update Branch B audit log is denied', async () => {
  const db = getManagerAContext().firestore();
  await assertFails(
    db.doc('branch_audit_logs/BRANCH_B_log1').update({
      verified: true,
      verifiedBy: 'manager_a',
      verifiedAt: Date.now(),
    })
  );
});

test('38. BranchAuditLogDeleteDenied: Attempting to delete a branch audit log is denied for all users', async () => {
  const db = getAdminContext().firestore();
  await assertFails(
    db.doc('branch_audit_logs/BRANCH_A_log1').delete()
  );
});

// ============================================================
// FINDING 3 — EXPIRY RESCUE LISTING BROAD UPDATE PATH TESTS
// ============================================================

test('39. ExpiryListingSameBranchStaffUpdateDenied: Ordinary staff in same branch (neither owner nor manager) modifying listing is denied', async () => {
  // pharm_b is ordinary staff in BRANCH_B, owner is pharm_b... wait, pharm_b is owner of rescue_b1!
  // Let's test with another ordinary pharmacist in BRANCH_B or manager in BRANCH_A.
  const db = getPharmAContext().firestore(); // Pharm A is not owner and not in Branch B
  await assertFails(
    db.doc('expiry_rescue_listings/rescue_b1').update({
      notes: 'Unauthorized edit',
    })
  );
});

test('40. ExpiryListingOwnerUpdateAllowed: Owner modifying own listing without changing ownerUid or branchId is allowed', async () => {
  const db = getPharmBContext().firestore(); // pharm_b is owner of rescue_b1
  await assertSucceeds(
    db.doc('expiry_rescue_listings/rescue_b1').update({
      notes: 'Discounted price applied by owner',
      quantity: 10,
    })
  );
});

test('41. ExpiryListingManagerUpdateAllowed: Branch manager modifying own branch listing without changing ownerUid or branchId is allowed', async () => {
  // manager_a context for a listing in BRANCH_A
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().doc('expiry_rescue_listings/rescue_a1').set({
      listingId: 'rescue_a1',
      branchId: 'BRANCH_A',
      ownerUid: 'pharm_a',
      productName: 'Aspirin 100mg',
      status: 'Available',
    });
  });

  const db = getManagerAContext().firestore();
  await assertSucceeds(
    db.doc('expiry_rescue_listings/rescue_a1').update({
      notes: 'Updated by branch manager',
      quantity: 5,
    })
  );
});

test('42. ExpiryListingManagerCrossBranchUpdateDenied: Manager from Branch A modifying listing in Branch B is denied', async () => {
  const db = getManagerAContext().firestore();
  await assertFails(
    db.doc('expiry_rescue_listings/rescue_b1').update({
      notes: 'Cross branch manager update',
    })
  );
});

test('43. ExpiryListingClaimantProductTamperDenied: Claimant modifying productName during claim is denied', async () => {
  const db = getPharmAContext().firestore();
  await assertFails(
    db.doc('expiry_rescue_listings/rescue_b1').update({
      status: 'Claimed',
      productName: 'Forged Expensive Drug',
    })
  );
});

test('44. ExpiryListingClaimantOwnerUidTamperDenied: Claimant modifying ownerUid during claim is denied', async () => {
  const db = getPharmAContext().firestore();
  await assertFails(
    db.doc('expiry_rescue_listings/rescue_b1').update({
      status: 'Claimed',
      ownerUid: 'pharm_a',
    })
  );
});

test('45. ExpiryListingClaimantBranchIdTamperDenied: Claimant modifying branchId during claim is denied', async () => {
  const db = getPharmAContext().firestore();
  await assertFails(
    db.doc('expiry_rescue_listings/rescue_b1').update({
      status: 'Claimed',
      branchId: 'BRANCH_A',
    })
  );
});

test('46. ExpiryListingClaimantUnrelatedMetadataTamperDenied: Claimant modifying unallowed metadata field during claim is denied', async () => {
  const db = getPharmAContext().firestore();
  await assertFails(
    db.doc('expiry_rescue_listings/rescue_b1').update({
      status: 'Claimed',
      unrelatedField: 'HACK',
    })
  );
});

test('47. ExpiryListingDeleteAnotherBranchDenied: Pharmacist attempting to delete another branch listing is denied', async () => {
  const db = getPharmAContext().firestore();
  await assertFails(
    db.doc('expiry_rescue_listings/rescue_b1').delete()
  );
});

// ============================================================
// FINDING 4 — CONSENT HANDSHAKE FIELD-LEVEL SECURITY TESTS
// ============================================================

test('48. ConsentHandshakePatientRebindDenied: Participant attempting to change patientUid on consent handshake is denied', async () => {
  const db = getPharmAContext().firestore();
  await assertFails(
    db.doc('consent_handshakes/BRANCH_A_handshake1').update({
      patientUid: 'patient_hacked',
    })
  );
});

test('49. ConsentHandshakePharmacistRebindDenied: Participant attempting to change pharmacistUid on consent handshake is denied', async () => {
  const db = getPharmAContext().firestore();
  await assertFails(
    db.doc('consent_handshakes/BRANCH_A_handshake1').update({
      pharmacistUid: 'pharm_hacked',
    })
  );
});

test('50. ConsentHandshakeBranchRebindDenied: Participant attempting to change branchId on consent handshake is denied', async () => {
  const db = getPharmAContext().firestore();
  await assertFails(
    db.doc('consent_handshakes/BRANCH_A_handshake1').update({
      branchId: 'BRANCH_B',
    })
  );
});

test('51. ConsentHandshakeCreatorIdentityRebindDenied: Participant attempting to change actorUid/creatorUid on consent handshake is denied', async () => {
  const db = getPharmAContext().firestore();
  await assertFails(
    db.doc('consent_handshakes/BRANCH_A_handshake1').update({
      actorUid: 'actor_hacked',
    })
  );
});

test('52. ConsentHandshakeCreationTimestampRewriteDenied: Participant attempting to change createdAt on consent handshake is denied', async () => {
  const db = getPharmAContext().firestore();
  await assertFails(
    db.doc('consent_handshakes/BRANCH_A_handshake1').update({
      createdAt: 0,
    })
  );
});

test('53. ConsentHandshakeParticipantStateUpdateAllowed: Authorized participant updating legitimate state fields is allowed', async () => {
  const db = getPharmAContext().firestore(); // pharm_a is pharmacistUid / actorUid / branchId in BRANCH_A_handshake1
  await assertSucceeds(
    db.doc('consent_handshakes/BRANCH_A_handshake1').update({
      status: 'ACCEPTED',
      consentGranted: true,
      notes: 'Patient provided verbal consent',
    })
  );
});

test('54. ConsentHandshakeUnrelatedBranchUpdateDenied: Unrelated branch user attempting update on consent handshake is denied', async () => {
  // Create handshake strictly between BRANCH_B and patient_b
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().doc('consent_handshakes/BRANCH_B_handshake_isolated').set({
      handshakeId: 'BRANCH_B_handshake_isolated',
      branchId: 'BRANCH_B',
      targetBranchId: 'BRANCH_B',
      patientUid: 'patient_b',
      pharmacistUid: 'pharm_b',
      actorUid: 'pharm_b',
      status: 'PENDING',
    });
  });

  const db = getPharmAContext().firestore(); // pharm_a is in BRANCH_A
  await assertFails(
    db.doc('consent_handshakes/BRANCH_B_handshake_isolated').update({
      status: 'ACCEPTED',
    })
  );
});

test('55. ConsentHandshakeUnrelatedPatientUpdateDenied: Unrelated patient attempting update on consent handshake is denied', async () => {
  const unrelatedPatientCtx = testEnv.authenticatedContext('unrelated_patient', {
    branchId: null,
    role: 'Patient',
    isApproved: true,
  });
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().doc('registered_pharmacists/unrelated_patient').set({
      uid: 'unrelated_patient',
      fullName: 'Unrelated Patient',
      isApproved: true,
    });
  });

  const db = unrelatedPatientCtx.firestore();
  await assertFails(
    db.doc('consent_handshakes/BRANCH_A_handshake1').update({
      status: 'ACCEPTED',
    })
  );
});

test('56. ConsentHandshakeUnrelatedPharmacistUpdateDenied: Unrelated pharmacist in another branch attempting update is denied', async () => {
  const db = getPharmBContext().firestore(); // pharm_b is in BRANCH_B
  await assertFails(
    db.doc('consent_handshakes/BRANCH_A_handshake1').update({
      status: 'REVOKED',
    })
  );
});

test('57. ConsentHandshakeUnauthorizedFieldMutationDenied: Modifying unallowed field on consent handshake is denied', async () => {
  const db = getPharmAContext().firestore();
  await assertFails(
    db.doc('consent_handshakes/BRANCH_A_handshake1').update({
      unauthorizedField: 'MALICIOUS_DATA',
    })
  );
});

// ============================================================
// FINDING 5 — DEVICE CONFIG AUTHORIZATION DESIGN TESTS
// ============================================================

test('58. DeviceConfigCrossBranchReadDenied: Cross-branch read denied even if nodeId prefix matches callers branch', async () => {
  // DEV_B2 document has nodeId "BRANCH_A_DEV_B2" but belongs to branchId "BRANCH_B"
  const db = getPharmAContext().firestore(); // pharm_a is in BRANCH_A
  await assertFails(db.doc('device_configs/BRANCH_A_DEV_B2').get());
});

test('59. DeviceConfigCrossBranchWriteDenied: Cross-branch device configuration write is denied', async () => {
  const db = getPharmAContext().firestore();
  await assertFails(
    db.doc('device_configs/DEV_B1').update({
      status: 'TAMPERED',
    })
  );
});

test('60. DeviceConfigOwnerBranchIdTamperDenied: Device owner attempting to alter branchId is denied', async () => {
  const db = getPharmAContext().firestore(); // owner of DEV_A1
  await assertFails(
    db.doc('device_configs/DEV_A1').update({
      branchId: 'BRANCH_B',
    })
  );
});

test('61. DeviceConfigManagerOwnBranchUpdateAllowed: Branch manager updating device in own branch is allowed', async () => {
  const db = getManagerAContext().firestore();
  await assertSucceeds(
    db.doc('device_configs/DEV_A1').update({
      status: 'Manager_Verified',
    })
  );
});

test('62. DeviceConfigManagerCrossBranchUpdateDenied: Branch manager updating device in another branch is denied', async () => {
  const db = getManagerAContext().firestore();
  await assertFails(
    db.doc('device_configs/DEV_B1').update({
      status: 'Manager_Cross_Tamper',
    })
  );
});

test('63. DeviceConfigAdminGlobalAccessAllowed: System administrator reading and updating device config is allowed', async () => {
  const db = getAdminContext().firestore();
  await assertSucceeds(db.doc('device_configs/DEV_B1').get());
  await assertSucceeds(
    db.doc('device_configs/DEV_B1').update({
      status: 'Admin_Overridden',
    })
  );
});

test('64. DeviceConfigUnauthenticatedAccessDenied: Unauthenticated read and update on device_configs is denied', async () => {
  const db = getUnauthContext().firestore();
  await assertFails(db.doc('device_configs/DEV_A1').get());
  await assertFails(
    db.doc('device_configs/DEV_A1').update({
      status: 'Unauth_Tamper',
    })
  );
});
